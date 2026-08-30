/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * An QW-AI-Code project
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.record

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Records the session to an .m4a (AAC-LC) file.
 *
 * The extension shipped a wasm LAME encoder for MP3; on Android the hardware AAC
 * encoder is available for free through MediaCodec, so the output is .m4a — same
 * players, smaller file, no native code to ship.
 */
class AacRecorder(
    descriptor: FileDescriptor,
    private val sampleRate: Int = 48000,
    bitrate: Int = 128000
) {

    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(descriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val bufferInfo = MediaCodec.BufferInfo()

    private var trackIndex = -1
    private var muxing = false
    private var totalFrames = 0L
    private var closed = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    /** Feed mono PCM16 samples at [sampleRate]. Safe to call from one thread only. */
    @Synchronized
    fun feed(samples: ShortArray, count: Int) {
        if (closed || count <= 0) return
        var offset = 0
        while (offset < count) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index < 0) {
                drain(false)
                continue
            }
            val input: ByteBuffer = codec.getInputBuffer(index) ?: continue
            input.clear()
            val capacityFrames = input.capacity() / 2
            val take = minOf(capacityFrames, count - offset)
            for (i in 0 until take) input.putShort(samples[offset + i])
            val presentationUs = totalFrames * 1_000_000L / sampleRate
            codec.queueInputBuffer(index, 0, take * 2, presentationUs, 0)
            totalFrames += take
            offset += take
            drain(false)
        }
    }

    private fun drain(endOfStream: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxing) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                }
                index >= 0 -> {
                    val output = codec.getOutputBuffer(index)
                    if (output != null && muxing &&
                        bufferInfo.size > 0 &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        output.position(bufferInfo.offset)
                        output.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, output, bufferInfo)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    @Synchronized
    fun finish() {
        if (closed) return
        closed = true
        try {
            val index = codec.dequeueInputBuffer(20_000)
            if (index >= 0) {
                codec.queueInputBuffer(index, 0, 0, totalFrames * 1_000_000L / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drain(true)
        } catch (_: Throwable) {
        }
        try {
            codec.stop()
        } catch (_: Throwable) {
        }
        codec.release()
        try {
            if (muxing) muxer.stop()
        } catch (_: Throwable) {
        }
        muxer.release()
    }

    val hasAudio: Boolean get() = totalFrames > 0
}
