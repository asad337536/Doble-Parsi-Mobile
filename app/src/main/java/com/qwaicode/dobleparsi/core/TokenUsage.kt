/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Built with care by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Gemini API token monitor.
 *
 * How the numbers are obtained (documented behaviour of the Live API):
 *  - every BidiGenerateContent server message may carry a `usageMetadata` block with
 *    promptTokenCount / responseTokenCount / totalTokenCount plus per modality
 *    details (AUDIO, TEXT) - see ai.google.dev/api/live#UsageMetadata.
 *  - the Gemini API does NOT expose an endpoint that returns the remaining daily
 *    quota. Rate limits (RPM / TPM / RPD / TPD) are per project, are shown in AI
 *    Studio, and requests-per-day counters reset at midnight Pacific time
 *    (ai.google.dev/gemini-api/docs/rate-limits).
 *
 * So the app counts locally, bucketed per Pacific day, and compares the total with
 * the daily budget the user copies from AI Studio. When Google answers with
 * 429 / RESOURCE_EXHAUSTED the quota failure and its retry delay are stored too,
 * which is the only authoritative "you are out of quota" signal on the wire.
 */
data class UsageSample(
    val promptTokens: Int = 0,
    val responseTokens: Int = 0,
    val totalTokens: Int = 0,
    val thoughtTokens: Int = 0,
    val cachedTokens: Int = 0,
    val audioInTokens: Int = 0,
    val audioOutTokens: Int = 0,
    val textTokens: Int = 0
) {
    val isEmpty: Boolean
        get() = promptTokens == 0 && responseTokens == 0 && totalTokens == 0 &&
            thoughtTokens == 0 && audioInTokens == 0 && audioOutTokens == 0 && textTokens == 0
}

data class UsageDay(
    val day: String = "",
    val input: Long = 0,
    val output: Long = 0,
    val total: Long = 0,
    val thoughts: Long = 0,
    val cached: Long = 0,
    val audioIn: Long = 0,
    val audioOut: Long = 0,
    val text: Long = 0,
    /** Largest session context seen that day (promptTokenCount peak). */
    val peakContext: Int = 0,
    /** Number of usage updates received, i.e. billed turns. */
    val updates: Int = 0,
    val sessions: Int = 0,
    val lastAt: Long = 0
)

data class QuotaState(
    val hit: Boolean = false,
    val message: String? = null,
    val retryAtMs: Long = 0,
    val at: Long = 0
)

data class UsageSnapshot(
    val today: UsageDay = UsageDay(),
    val history: List<UsageDay> = emptyList(),
    val quota: QuotaState = QuotaState(),
    val resetAtMs: Long = 0
)

class UsageStore private constructor(context: Context) {

    companion object {
        private const val PREFS = "doble_parsi_usage"
        private const val KEY_DAYS = "days"
        private const val KEY_QUOTA = "quota"
        private const val MAX_DAYS = 14

        /** ai.dev usage dashboard - the authoritative view of the project quota. */
        const val DASHBOARD_URL = "https://ai.dev/usage?tab=rate-limit"

        /** RPD/TPD counters reset at midnight Pacific time. */
        private val PACIFIC: ZoneId =
            runCatching { ZoneId.of("America/Los_Angeles") }.getOrElse { ZoneOffset.ofHours(-8) }

        @Volatile
        private var instance: UsageStore? = null

        fun get(context: Context): UsageStore =
            instance ?: synchronized(this) {
                instance ?: UsageStore(context).also { instance = it }
            }

        fun dayKey(atMs: Long = System.currentTimeMillis()): String =
            Instant.ofEpochMilli(atMs).atZone(PACIFIC).toLocalDate().toString()

        fun nextResetMs(atMs: Long = System.currentTimeMillis()): Long =
            Instant.ofEpochMilli(atMs).atZone(PACIFIC).toLocalDate().plusDays(1)
                .atStartOfDay(PACIFIC).toInstant().toEpochMilli()
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val days = LinkedHashMap<String, UsageDay>()
    private var quota = QuotaState()

    private val _state = MutableStateFlow(UsageSnapshot())
    val state: StateFlow<UsageSnapshot> = _state.asStateFlow()

    val value: UsageSnapshot get() = _state.value

    init {
        load()
        publish()
    }

    // -- recording ---------------------------------------------------------

    /** Called for every server message that carries usageMetadata. */
    @Synchronized
    fun record(sample: UsageSample) {
        if (sample.isEmpty) return
        val key = dayKey()
        val current = days[key] ?: UsageDay(day = key)
        days[key] = current.copy(
            input = current.input + sample.promptTokens,
            output = current.output + sample.responseTokens,
            total = current.total + if (sample.totalTokens > 0) {
                sample.totalTokens
            } else {
                sample.promptTokens + sample.responseTokens
            },
            thoughts = current.thoughts + sample.thoughtTokens,
            cached = current.cached + sample.cachedTokens,
            audioIn = current.audioIn + sample.audioInTokens,
            audioOut = current.audioOut + sample.audioOutTokens,
            text = current.text + sample.textTokens,
            peakContext = maxOf(current.peakContext, sample.promptTokens),
            updates = current.updates + 1,
            lastAt = System.currentTimeMillis()
        )
        trim()
        save()
        publish()
    }

    @Synchronized
    fun startSession() {
        val key = dayKey()
        val current = days[key] ?: UsageDay(day = key)
        days[key] = current.copy(
            sessions = current.sessions + 1,
            lastAt = System.currentTimeMillis()
        )
        if (quota.hit && quota.retryAtMs in 1..System.currentTimeMillis()) quota = QuotaState()
        trim()
        save()
        publish()
    }

    /** 429 / RESOURCE_EXHAUSTED from Google: the only hard "out of quota" signal. */
    @Synchronized
    fun noteQuota(message: String?, retryMs: Long) {
        quota = QuotaState(
            hit = true,
            message = message?.take(400),
            retryAtMs = if (retryMs > 0) System.currentTimeMillis() + retryMs else 0,
            at = System.currentTimeMillis()
        )
        save()
        publish()
    }

    @Synchronized
    fun clearQuota() {
        if (!quota.hit) return
        quota = QuotaState()
        save()
        publish()
    }

    @Synchronized
    fun clearAll() {
        days.clear()
        quota = QuotaState()
        save()
        publish()
    }

    /** Percentage of the daily budget used, or null when no budget is set. */
    fun usedFraction(dailyLimit: Int): Float? {
        if (dailyLimit <= 0) return null
        return (value.today.total.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f)
    }

    fun remaining(dailyLimit: Int): Long? {
        if (dailyLimit <= 0) return null
        return maxOf(0L, dailyLimit.toLong() - value.today.total)
    }

    // -- persistence -------------------------------------------------------

    private fun publish() {
        val key = dayKey()
        _state.value = UsageSnapshot(
            today = days[key] ?: UsageDay(day = key),
            history = days.values.sortedByDescending { it.day },
            quota = quota,
            resetAtMs = nextResetMs()
        )
    }

    private fun trim() {
        if (days.size <= MAX_DAYS) return
        val keep = days.keys.sortedDescending().take(MAX_DAYS).toSet()
        val drop = days.keys.filterNot { keep.contains(it) }
        drop.forEach { days.remove(it) }
    }

    private fun load() {
        runCatching {
            val raw = prefs.getString(KEY_DAYS, null) ?: return@runCatching
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val day = item.optString("day")
                if (day.isEmpty()) continue
                days[day] = UsageDay(
                    day = day,
                    input = item.optLong("input"),
                    output = item.optLong("output"),
                    total = item.optLong("total"),
                    thoughts = item.optLong("thoughts"),
                    cached = item.optLong("cached"),
                    audioIn = item.optLong("audioIn"),
                    audioOut = item.optLong("audioOut"),
                    text = item.optLong("text"),
                    peakContext = item.optInt("peakContext"),
                    updates = item.optInt("updates"),
                    sessions = item.optInt("sessions"),
                    lastAt = item.optLong("lastAt")
                )
            }
        }
        runCatching {
            val raw = prefs.getString(KEY_QUOTA, null) ?: return@runCatching
            val item = JSONObject(raw)
            quota = QuotaState(
                hit = item.optBoolean("hit"),
                message = item.optString("message").takeIf { it.isNotEmpty() },
                retryAtMs = item.optLong("retryAtMs"),
                at = item.optLong("at")
            )
        }
    }

    private fun save() {
        val array = JSONArray()
        days.values.sortedBy { it.day }.forEach { day ->
            array.put(
                JSONObject()
                    .put("day", day.day)
                    .put("input", day.input)
                    .put("output", day.output)
                    .put("total", day.total)
                    .put("thoughts", day.thoughts)
                    .put("cached", day.cached)
                    .put("audioIn", day.audioIn)
                    .put("audioOut", day.audioOut)
                    .put("text", day.text)
                    .put("peakContext", day.peakContext)
                    .put("updates", day.updates)
                    .put("sessions", day.sessions)
                    .put("lastAt", day.lastAt)
            )
        }
        val quotaJson = JSONObject()
            .put("hit", quota.hit)
            .put("message", quota.message ?: "")
            .put("retryAtMs", quota.retryAtMs)
            .put("at", quota.at)
        runCatching {
            prefs.edit()
                .putString(KEY_DAYS, array.toString())
                .putString(KEY_QUOTA, quotaJson.toString())
                .apply()
        }
    }
}
