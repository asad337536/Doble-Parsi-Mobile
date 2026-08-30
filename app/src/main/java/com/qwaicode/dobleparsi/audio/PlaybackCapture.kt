/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * An QW-AI-Code project
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Process
import android.util.Log

/**
 * Captures whatever the phone is playing (YouTube, Telegram, a browser, any player)
 * through MediaProjection playback capture — the Android replacement for
 * chrome.tabCapture in the extension.
 *
 * Notes that matter in practice:
 *  • Only USAGE_MEDIA / GAME / UNKNOWN streams can be captured. Voice calls cannot,
 *    and an app that sets ALLOW_CAPTURE_BY_NONE opts itself out (Netflix and friends).
 *  • Our own uid is excluded so the dub we play back is never captured again.
 */
class PlaybackCapture(private val projection: MediaProjection) {

    var sampleRate: Int = 48000
        private set
    var channels: Int = 1
        private set

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    private fun build(rate: Int, channelMask: Int): AudioRecord? {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(Process.myUid())
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(channelMask)
            .build()

        val minBytes = AudioRecord.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return null

        return try {
            val built = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBytes * 4)
                .setAudioPlaybackCaptureConfig(config)
                .build()
            if (built.state == AudioRecord.STATE_INITIALIZED) built else { built.release(); null }
        } catch (t: Throwable) {
            Log.w("PlaybackCapture", "build failed at $rate/$channelMask: ${t.message}")
            null
        }
    }

    /**
     * Starts capture. [onMono] receives mono float samples at [sampleRate] on a
     * dedicated high priority thread.
     */
    fun start(onMono: (FloatArray, Int) -> Unit): Boolean {
        if (running) return true

        val attempts = listOf(
            48000 to AudioFormat.CHANNEL_IN_STEREO,
            48000 to AudioFormat.CHANNEL_IN_MONO,
            44100 to AudioFormat.CHANNEL_IN_STEREO,
            44100 to AudioFormat.CHANNEL_IN_MONO,
            16000 to AudioFormat.CHANNEL_IN_MONO
        )

        var created: AudioRecord? = null
        for ((rate, mask) in attempts) {
            created = build(rate, mask)
            if (created != null) {
                sampleRate = rate
                channels = if (mask == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
                break
            }
        }
        val active = created ?: return false

        record = active
        running = true
        active.startRecording()

        val frames = sampleRate / 50 // 20 ms blocks
        val shorts = ShortArray(frames * channels)
        val mono = FloatArray(frames)

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (running) {
                val read = try {
                    active.read(shorts, 0, shorts.size)
                } catch (_: Throwable) {
                    break
                }
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_DEAD_OBJECT) break
                    continue
                }
                val frameCount = read / channels
                if (channels == 1) {
                    for (i in 0 until frameCount) mono[i] = shorts[i] / 32768f
                } else {
                    var index = 0
                    for (i in 0 until frameCount) {
                        val left = shorts[index].toInt()
                        val right = shorts[index + 1].toInt()
                        index += 2
                        mono[i] = ((left + right) * 0.5f) / 32768f
                    }
                }
                onMono(mono, frameCount)
            }
        }, "playback-capture").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        record?.release()
        record = null
    }
}
