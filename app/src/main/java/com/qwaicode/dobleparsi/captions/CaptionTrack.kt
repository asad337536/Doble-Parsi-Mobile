/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Crafted by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.captions

/** Live caption assembly + SRT/VTT export — ported from src/captions.js. */
data class Cue(val start: Long, val end: Long, val text: String)

class CaptionTrack {

    private companion object {
        val SENTENCE_END = Regex("[.!?؟…۔。！？][\"'”»)\\]]?$")
        val NO_SPACE_BEFORE = Regex("^[,.!?;:؟،؛…)\\]}»\"']")
        const val IDLE_FLUSH_MS = 900L
        const val MAX_CUE_MS = 7000L
        const val MAX_CUE_CHARS = 140
        const val MIN_SENTENCE_CHARS = 12
        const val MIN_CUE_MS = 700L
    }

    private val cues = ArrayList<Cue>()
    private var buffer = ""
    private var startMs = 0L
    private var lastMs = 0L

    val isEmpty: Boolean get() = cues.isEmpty() && buffer.isBlank()

    @Synchronized
    fun push(text: String, atMs: Long) {
        if (text.isBlank()) return
        if (buffer.isBlank()) startMs = atMs
        buffer = join(buffer, text)
        lastMs = atMs

        val trimmed = buffer.trim()
        val longEnough = trimmed.length >= MIN_SENTENCE_CHARS
        if ((SENTENCE_END.containsMatchIn(trimmed) && longEnough) ||
            trimmed.length >= MAX_CUE_CHARS ||
            atMs - startMs >= MAX_CUE_MS
        ) {
            flush(atMs)
        }
    }

    /** Called a few times a second so silence closes the open cue. */
    @Synchronized
    fun tick(nowMs: Long) {
        if (buffer.isNotBlank() && nowMs - lastMs >= IDLE_FLUSH_MS) flush(nowMs)
    }

    @Synchronized
    fun flush(endMs: Long) {
        val text = buffer.trim()
        buffer = ""
        if (text.isEmpty()) return
        val end = maxOf(endMs, startMs + MIN_CUE_MS)
        cues.add(Cue(startMs, end, text))
    }

    /** Final cues with delay compensation and no overlap. */
    @Synchronized
    fun finalize(offsetMs: Int): List<Cue> {
        flush(lastMs)
        val shifted = cues.map { cue ->
            val duration = maxOf(MIN_CUE_MS, cue.end - cue.start)
            val start = maxOf(0L, cue.start - offsetMs)
            Cue(start, start + duration, cue.text)
        }.sortedBy { it.start }.toMutableList()

        for (i in 0 until shifted.size - 1) {
            if (shifted[i].end > shifted[i + 1].start) {
                val end = maxOf(shifted[i].start + 200L, shifted[i + 1].start - 40L)
                shifted[i] = shifted[i].copy(end = end)
            }
        }
        return shifted
    }

    private fun join(previous: String, next: String): String {
        if (previous.isEmpty()) return next.trimStart()
        if (previous.last().isWhitespace() || next.first().isWhitespace()) return previous + next
        if (NO_SPACE_BEFORE.containsMatchIn(next)) return previous + next
        return "$previous $next"
    }
}

object Subtitles {

    fun toSrt(cues: List<Cue>): String = cues.mapIndexed { index, cue ->
        "${index + 1}\n${stamp(cue.start, ",")} --> ${stamp(cue.end, ",")}\n${cue.text}\n"
    }.joinToString("\n")

    fun toVtt(cues: List<Cue>): String {
        val body = cues.joinToString("\n") { cue ->
            "${stamp(cue.start, ".")} --> ${stamp(cue.end, ".")}\n${cue.text}\n"
        }
        return "WEBVTT\n\n$body"
    }

    private fun stamp(ms: Long, separator: String): String {
        val total = maxOf(0L, ms)
        val hours = total / 3600000
        val minutes = (total % 3600000) / 60000
        val seconds = (total % 60000) / 1000
        val millis = total % 1000
        return "%02d:%02d:%02d%s%03d".format(hours, minutes, seconds, separator, millis)
    }
}
