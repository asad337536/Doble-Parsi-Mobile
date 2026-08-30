/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Made in the open by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.audio

/** Lock light float ring buffer shared between the network, player and record threads. */
class FloatRingBuffer(capacity: Int) {

    private val ring = FloatArray(capacity)
    private var readIndex = 0
    private var writeIndex = 0
    private var count = 0

    val available: Int
        @Synchronized get() = count

    @Synchronized
    fun clear() {
        readIndex = 0
        writeIndex = 0
        count = 0
    }

    /** Writes [length] samples, dropping the oldest data when it would exceed [max]. */
    @Synchronized
    fun write(samples: FloatArray, length: Int = samples.size, max: Int = ring.size - 1): Int {
        if (length <= 0) return 0
        var dropped = 0
        val overflow = count + length - max
        if (overflow > 0) {
            val drop = minOf(overflow, count)
            readIndex = (readIndex + drop) % ring.size
            count -= drop
            dropped = drop
        }
        var offset = 0
        var remaining = minOf(length, ring.size)
        while (remaining > 0) {
            val chunk = minOf(remaining, ring.size - writeIndex)
            samples.copyInto(ring, writeIndex, offset, offset + chunk)
            writeIndex = (writeIndex + chunk) % ring.size
            offset += chunk
            remaining -= chunk
        }
        count = minOf(count + minOf(length, ring.size), ring.size)
        return dropped
    }

    /** Reads up to [length] samples into [target]; returns how many were read. */
    @Synchronized
    fun read(target: FloatArray, length: Int = target.size): Int {
        val take = minOf(length, count)
        var offset = 0
        var remaining = take
        while (remaining > 0) {
            val chunk = minOf(remaining, ring.size - readIndex)
            ring.copyInto(target, offset, readIndex, readIndex + chunk)
            readIndex = (readIndex + chunk) % ring.size
            offset += chunk
            remaining -= chunk
        }
        count -= take
        if (take < length) target.fill(0f, take, length)
        return take
    }
}
