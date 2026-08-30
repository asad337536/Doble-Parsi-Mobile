/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Crafted by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Windowed sinc (Kaiser) resampler with continuous state across chunks —
 * ported from src/resampler.js.
 *
 * Linear interpolation is not good enough here: 48k -> 16k without an anti alias
 * filter folds everything above 8 kHz onto speech and wrecks the model input, and
 * 24k -> 48k with linear interpolation creates the imaging that is heard as
 * roughness on the dub voice.
 */
class SincResampler(private val inputRate: Int, private val outputRate: Int) {

    private companion object {
        const val LEFT = 15
        const val RIGHT = 16
        const val TAPS = LEFT + RIGHT + 1
        const val SUBPHASES = 512
        const val KAISER_BETA = 9.0
        const val CUTOFF_GUARD = 0.92

        val kernelCache = HashMap<String, FloatArray>()

        fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val half = x / 2.0
            for (k in 1 until 32) {
                term *= (half / k) * (half / k)
                sum += term
                if (term < sum * 1e-16) break
            }
            return sum
        }

        fun sinc(x: Double): Double {
            if (x == 0.0) return 1.0
            val pix = PI * x
            return sin(pix) / pix
        }

        @Synchronized
        fun buildKernel(cutoff: Double): FloatArray {
            val key = String.format("%.6f", cutoff)
            kernelCache[key]?.let { return it }

            val table = FloatArray(SUBPHASES * TAPS)
            val support = RIGHT.toDouble()
            val norm = besselI0(KAISER_BETA)

            for (phase in 0 until SUBPHASES) {
                val frac = phase.toDouble() / SUBPHASES
                var sum = 0.0
                val base = phase * TAPS
                for (tap in 0 until TAPS) {
                    val offset = tap - LEFT - frac
                    val ratio = offset / support
                    var weight = 0.0
                    if (ratio > -1 && ratio < 1) {
                        weight = besselI0(KAISER_BETA * sqrt(1 - ratio * ratio)) / norm
                    }
                    val value = 2 * cutoff * sinc(2 * cutoff * offset) * weight
                    table[base + tap] = value.toFloat()
                    sum += value
                }
                if (sum != 0.0) {
                    val scale = (1.0 / sum).toFloat()
                    for (tap in 0 until TAPS) table[base + tap] *= scale
                }
            }

            kernelCache[key] = table
            return table
        }
    }

    private val ratio: Double = inputRate.toDouble() / outputRate.toDouble()
    private val kernel: FloatArray
    private var history = FloatArray(LEFT)
    private var position: Double = LEFT.toDouble()

    val isPassthrough: Boolean = inputRate == outputRate

    init {
        val limit = if (ratio > 1.0) 0.5 / ratio else 0.5
        kernel = buildKernel(limit * CUTOFF_GUARD)
    }

    fun process(input: FloatArray, count: Int = input.size): FloatArray {
        if (count <= 0) return FloatArray(0)
        if (isPassthrough) return input.copyOf(count)

        val merged = FloatArray(history.size + count)
        history.copyInto(merged, 0)
        input.copyInto(merged, history.size, 0, count)

        val limit = merged.size - RIGHT
        val estimate = maxOf(0, ceil((limit - position) / ratio).toInt() + 1)
        if (estimate == 0) {
            history = merged
            return FloatArray(0)
        }
        val out = FloatArray(estimate)

        var written = 0
        while (position < limit && written < estimate) {
            val base = floor(position).toInt()
            val frac = position - base
            var phase = (frac * SUBPHASES).toInt()
            if (phase >= SUBPHASES) phase = SUBPHASES - 1
            val offset = phase * TAPS
            val start = base - LEFT

            var acc = 0f
            for (tap in 0 until TAPS) acc += kernel[offset + tap] * merged[start + tap]

            out[written] = acc
            written += 1
            position += ratio
        }

        val consumed = floor(position).toInt() - LEFT
        history = merged.copyOfRange(consumed.coerceIn(0, merged.size), merged.size)
        position -= consumed

        return if (written == estimate) out else out.copyOf(written)
    }

    /** Flush the filter tail at end of stream. */
    fun flush(): FloatArray = if (isPassthrough) FloatArray(0) else process(FloatArray(RIGHT + 1))

    fun reset() {
        history = FloatArray(LEFT)
        position = LEFT.toDouble()
    }
}
