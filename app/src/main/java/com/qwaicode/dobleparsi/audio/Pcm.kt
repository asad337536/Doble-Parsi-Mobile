/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Source and releases: github.com/QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.audio

/**
 * Low level PCM helpers — ported from src/pcm.js.
 *
 * The odd-byte carry in [Int16StreamReader] matters: Gemini audio chunks can end
 * mid-sample. Dropping that byte shifts the whole stream by one byte and turns the
 * dub into full scale white noise.
 */
object Pcm {

    private const val INT16_MAX = 32767
    private const val INT16_MIN = -32768

    /** Soft limiter, transparent below -1 dBFS, stops hard clipping on the record mix. */
    fun softLimitInPlace(samples: FloatArray, count: Int = samples.size): FloatArray {
        val threshold = 0.891251f
        val range = 1f - threshold
        for (i in 0 until count) {
            val value = samples[i]
            if (!value.isFinite()) {
                samples[i] = 0f
                continue
            }
            val magnitude = if (value < 0f) -value else value
            if (magnitude <= threshold) continue
            val over = (magnitude - threshold) / range
            val shaped = threshold + range * Math.tanh(over.toDouble()).toFloat()
            samples[i] = if (value < 0f) -shaped else shaped
        }
        return samples
    }

    /** Float (-1..1) to symmetric clamped Int16, no dither. */
    fun floatToShort(source: FloatArray, count: Int, target: ShortArray) {
        for (i in 0 until count) {
            val value = source[i]
            if (!value.isFinite()) {
                target[i] = 0
                continue
            }
            val scaled = Math.round(value * INT16_MAX)
            target[i] = when {
                scaled > INT16_MAX -> INT16_MAX.toShort()
                scaled < INT16_MIN -> INT16_MIN.toShort()
                else -> scaled.toShort()
            }
        }
    }

    fun shortToFloat(source: ShortArray, count: Int, target: FloatArray) {
        for (i in 0 until count) target[i] = source[i] / 32768f
    }
}

/** Byte accurate PCM16 LE reader that carries an unpaired byte to the next chunk. */
class Int16StreamReader {

    private var carry: Int = -1

    fun push(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty()) return FloatArray(0)

        val hasCarry = carry >= 0
        val total = bytes.size + if (hasCarry) 1 else 0
        val frames = total shr 1
        val leftover = total and 1

        val out = FloatArray(frames)
        var index = 0
        var cursor = 0

        if (hasCarry && frames > 0) {
            val low = carry and 0xFF
            val high = bytes[0].toInt() and 0xFF
            out[index] = (((low or (high shl 8)) shl 16) shr 16) / 32768f
            index += 1
            cursor = 1
        }

        while (index < frames) {
            val low = bytes[cursor].toInt() and 0xFF
            val high = bytes[cursor + 1].toInt() and 0xFF
            cursor += 2
            out[index] = (((low or (high shl 8)) shl 16) shr 16) / 32768f
            index += 1
        }

        carry = if (leftover == 1) bytes[bytes.size - 1].toInt() and 0xFF else -1
        return out
    }

    fun reset() {
        carry = -1
    }
}
