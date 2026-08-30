/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Source and releases: github.com/QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Continuous ring buffer dub playback — the AudioTrack twin of audio/dub-player-worklet.js.
 *
 * One AudioTrack, one continuous stream: per chunk playback objects click at every
 * chunk boundary. Underruns fade to silence over a couple of milliseconds instead of
 * producing a hard edge, and the buffer is capped so latency cannot grow forever.
 *
 * The track is marked ALLOW_CAPTURE_BY_NONE so our own dub is never re-captured and
 * fed back into the model.
 */
class DubPlayer(
    private val sampleRate: Int = 48000,
    private val prebufferMs: Int = 180,
    private val maxBufferMs: Int = 9000,
    private val fadeMs: Int = 2,
    private val onActiveChanged: (Boolean) -> Unit,
    private val onPlayed: ((FloatArray, Int) -> Unit)? = null
) {

    private val ringFrames = 1 shl 19
    private val ring = FloatRingBuffer(ringFrames)
    private val prebufferFrames = maxOf(64, prebufferMs * sampleRate / 1000)
    private val maxFrames = minOf(ringFrames - 1, maxBufferMs * sampleRate / 1000)
    private val fadeFrames = maxOf(8, fadeMs * sampleRate / 1000)
    private val rampStep = 1f / fadeFrames

    private val blockFrames = sampleRate / 50 // 20 ms
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var draining = false
    @Volatile private var streaming = false
    @Volatile private var active = false
    @Volatile private var volume = 1f
    private var gain = 0f
    var droppedFrames: Long = 0
        private set

    val bufferedFrames: Int get() = ring.available

    fun start() {
        if (running) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(blockFrames * 2 * 4)

        val created = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBytes * 2)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
            .build()

        created.setVolume(volume)
        created.play()
        track = created
        running = true
        thread = Thread({ loop(created) }, "dub-player").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        track?.setVolume(volume)
    }

    /** Push freshly decoded model audio, already resampled to the track rate. */
    fun push(samples: FloatArray, length: Int = samples.size) {
        droppedFrames += ring.write(samples, length, maxFrames)
    }

    /**
     * Pushes [ms] of silence into the stream.
     *
     * Only used when the user deliberately nudges the dub behind the original; the
     * default is 0 ms, i.e. the dub starts exactly with the original audio.
     */
    fun pushSilence(ms: Int) {
        if (ms <= 0) return
        val frames = ms * sampleRate / 1000
        if (frames <= 0) return
        val block = FloatArray(minOf(frames, 1 shl 15))
        var remaining = frames
        while (remaining > 0) {
            val take = minOf(remaining, block.size)
            ring.write(block, take, maxFrames)
            remaining -= take
        }
    }

    fun reset() {
        ring.clear()
        streaming = false
        gain = 0f
    }

    fun drain() {
        draining = true
    }

    /** True once the tail has been played out. */
    fun isDrained(): Boolean = ring.available == 0 && !streaming

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        try {
            track?.pause()
            track?.flush()
            track?.stop()
        } catch (_: Throwable) {
        }
        track?.release()
        track = null
        setActive(false)
    }

    private fun loop(target: AudioTrack) {
        val block = FloatArray(blockFrames)
        val shorts = ShortArray(blockFrames)
        val silenceSleepMs = 5L

        while (running) {
            if (!streaming) {
                val ready = ring.available >= prebufferFrames || (draining && ring.available > 0)
                if (!ready) {
                    setActive(ring.available > 0)
                    try {
                        Thread.sleep(silenceSleepMs)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
                streaming = true
                gain = 0f
            }

            val take = minOf(blockFrames, maxOf(0, ring.available))
            if (take == 0) {
                streaming = false
                gain = 0f
                setActive(false)
                continue
            }

            ring.read(block, take)
            val starving = take < blockFrames

            for (i in 0 until take) {
                var g = gain + rampStep
                if (g > 1f) g = 1f
                if (starving) {
                    val tail = (take - i).toFloat() / fadeFrames
                    if (tail < g) g = tail
                }
                gain = g
                block[i] = block[i] * g
            }

            Pcm.softLimitInPlace(block, take)
            onPlayed?.invoke(block, take)
            Pcm.floatToShort(block, take, shorts)
            try {
                target.write(shorts, 0, take)
            } catch (_: Throwable) {
                break
            }

            setActive(true)

            if (starving) {
                streaming = false
                gain = 0f
            }
        }
    }

    private fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        onActiveChanged(value)
    }
}
