/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Source and releases: github.com/QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.net

import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import com.qwaicode.dobleparsi.core.UsageSample
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Live (BidiGenerateContent) client for simultaneous dubbing —
 * ported from src/live-client.js.
 *
 * In : PCM 16 bit mono 16 kHz.  Out: PCM 16 bit mono 24 kHz + transcripts.
 *
 * The gemini live translate model replicates the original speaker's voice by
 * itself and ignores speechConfig, so there is no voice picker.
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val model: String,
    private val targetLanguageCode: String,
    private val sourceTranscription: Boolean,
    private val dubTranscription: Boolean,
    private val listener: Listener
) {

    interface Listener {
        fun onReady()
        fun onAudio(pcm: ByteArray, sampleRate: Int)
        fun onSourceText(text: String)
        fun onDubText(text: String)
        fun onInterrupted()
        fun onError(code: String, detail: String?)

        /** Token accounting straight from the server's usageMetadata block. */
        fun onUsage(sample: UsageSample)

        /** 429 / RESOURCE_EXHAUSTED: the project ran into a Gemini quota. */
        fun onQuota(message: String?, retryMs: Long)
    }

    private companion object {
        const val ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val INPUT_MIME = "audio/pcm;rate=16000"
        const val DEFAULT_OUTPUT_RATE = 24000
        const val MAX_PENDING_CHUNKS = 24
    }

    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val thread = HandlerThread("gemini-live").apply { start() }
    private val handler = Handler(thread.looper)

    private var socket: WebSocket? = null
    private var generation = 0
    private var ready = false
    private var closed = false
    private var reconnectScheduled = false
    private var resumptionHandle: String? = null
    private val pending = ArrayDeque<ByteArray>()

    fun connect() = handler.post { openSocket() }

    private fun modelPath(): String = if (model.startsWith("models/")) model else "models/$model"

    private fun openSocket() {
        if (closed) return
        val url = "$ENDPOINT?key=$apiKey"
        val request = Request.Builder().url(url).build()
        val currentGeneration = generation
        ready = false
        socket = http.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                handler.post {
                    if (currentGeneration != generation || webSocket != socket) return@post
                    sendSetup()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handler.post { handleMessage(text, webSocket, currentGeneration) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handler.post { handleMessage(bytes.utf8(), webSocket, currentGeneration) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handler.post {
                    if (closed || currentGeneration != generation || webSocket != socket) return@post
                    ready = false
                    val httpCode = response?.code ?: 0
                    val httpMessage = response?.message
                    if (httpCode == 429) {
                        val detail = if (httpMessage.isNullOrBlank()) t.message else httpMessage
                        listener.onQuota(detail, 0L)
                        listener.onError("err.quota", detail)
                    } else {
                        listener.onError("err.socket", t.message)
                    }
                    scheduleReconnect(2000)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handler.post {
                    if (closed || currentGeneration != generation || webSocket != socket) return@post
                    ready = false
                    if (code == 1000) return@post
                    scheduleReconnect(1500)
                }
            }
        })
    }

    private fun sendSetup() {
        val translation = JSONObject()
            .put("targetLanguageCode", targetLanguageCode)
            .put("echoTargetLanguage", true)

        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("translationConfig", translation)

        val setup = JSONObject()
            .put("model", modelPath())
            .put("generationConfig", generationConfig)

        if (sourceTranscription) setup.put("inputAudioTranscription", JSONObject())
        if (dubTranscription) setup.put("outputAudioTranscription", JSONObject())

        val resumption = JSONObject()
        resumptionHandle?.let { resumption.put("handle", it) }
        setup.put("sessionResumption", resumption)

        sendJson(JSONObject().put("setup", setup))
    }

    /** Queue safe audio push; chunks sent before setupComplete are replayed after it. */
    fun sendAudio(pcm16: ByteArray) {
        handler.post {
            if (closed) return@post
            if (!ready) {
                pending.addLast(pcm16)
                while (pending.size > MAX_PENDING_CHUNKS) pending.removeFirst()
                return@post
            }
            sendAudioNow(pcm16)
        }
    }

    private fun sendAudioNow(pcm16: ByteArray) {
        val audio = JSONObject()
            .put("data", Base64.encodeToString(pcm16, Base64.NO_WRAP))
            .put("mimeType", INPUT_MIME)
        sendJson(JSONObject().put("realtimeInput", JSONObject().put("audio", audio)))
    }

    private fun sendJson(payload: JSONObject) {
        socket?.send(payload.toString())
    }

    private fun handleMessage(text: String, webSocket: WebSocket, currentGeneration: Int) {
        if (currentGeneration != generation || webSocket != socket) return

        val message = try {
            JSONObject(text)
        } catch (_: Throwable) {
            return
        }

        // Usage metadata can ride along with any server message.
        val usage = message.optJSONObject("usageMetadata") ?: message.optJSONObject("usage_metadata")
        if (usage != null) {
            val sample = parseUsage(usage)
            if (!sample.isEmpty) listener.onUsage(sample)
        }

        message.optJSONObject("error")?.let { error ->
            val detail = error.optString("message")
            val status = error.optString("status")
            val code = error.optInt("code")
            if (code == 429 || status == "RESOURCE_EXHAUSTED" ||
                detail.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                detail.contains("quota", ignoreCase = true)
            ) {
                listener.onQuota(detail, retryDelayOf(error, detail))
                listener.onError("err.quota", detail)
            } else {
                listener.onError("err.api", detail)
            }
            return
        }

        val resumption = message.optJSONObject("sessionResumptionUpdate")
            ?: message.optJSONObject("session_resumption_update")
        if (resumption != null) {
            val handle = resumption.optString("newHandle").ifEmpty { resumption.optString("new_handle") }
            if (resumption.optBoolean("resumable") && handle.isNotEmpty()) resumptionHandle = handle
        }

        val goAway = message.optJSONObject("goAway") ?: message.optJSONObject("go_away")
        if (goAway != null) {
            val left = secondsToMs(goAway.optString("timeLeft").ifEmpty { goAway.optString("time_left") })
            scheduleReconnect(maxOf(0L, (left ?: 0L) - 2000L))
        }

        if (message.has("setupComplete") || message.has("setup_complete")) {
            ready = true
            listener.onReady()
            val queued = ArrayList(pending)
            pending.clear()
            queued.forEach { sendAudioNow(it) }
            return
        }

        val content = message.optJSONObject("serverContent")
            ?: message.optJSONObject("server_content")
            ?: return

        // A cut turn must reset the byte carry, otherwise the next sample is read
        // at the wrong offset and the audio turns to noise.
        if (content.optBoolean("interrupted")) {
            listener.onInterrupted()
            return
        }

        val sourceText = content.optJSONObject("inputTranscription")
            ?: content.optJSONObject("input_transcription")
        sourceText?.optString("text")?.takeIf { it.isNotBlank() }?.let { listener.onSourceText(it) }

        val dubText = content.optJSONObject("outputTranscription")
            ?: content.optJSONObject("output_transcription")
        dubText?.optString("text")?.takeIf { it.isNotBlank() }?.let { listener.onDubText(it) }

        val turn = content.optJSONObject("modelTurn") ?: content.optJSONObject("model_turn")
        val parts = turn?.optJSONArray("parts") ?: return
        for (i in 0 until parts.length()) {
            if (currentGeneration != generation || webSocket != socket) return
            val part = parts.optJSONObject(i) ?: continue
            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data") ?: continue
            val data = inline.optString("data")
            if (data.isEmpty()) continue
            val mime = inline.optString("mimeType").ifEmpty { inline.optString("mime_type") }
            val bytes = try {
                Base64.decode(data, Base64.DEFAULT)
            } catch (_: Throwable) {
                continue
            }
            listener.onAudio(bytes, rateOf(mime))
        }
    }

    /** ai.google.dev/api/live#UsageMetadata */
    private fun parseUsage(usage: org.json.JSONObject): UsageSample {
        val prompt = usage.optInt("promptTokenCount", usage.optInt("prompt_token_count"))
        val response = maxOf(
            usage.optInt("responseTokenCount", usage.optInt("response_token_count")),
            usage.optInt("candidatesTokenCount", usage.optInt("candidates_token_count"))
        )
        val total = usage.optInt("totalTokenCount", usage.optInt("total_token_count"))
        val thoughts = usage.optInt("thoughtsTokenCount", usage.optInt("thoughts_token_count"))
        val cached = usage.optInt("cachedContentTokenCount", usage.optInt("cached_content_token_count"))

        val promptDetails = usage.optJSONArray("promptTokensDetails")
            ?: usage.optJSONArray("prompt_tokens_details")
        val responseDetails = usage.optJSONArray("responseTokensDetails")
            ?: usage.optJSONArray("response_tokens_details")
            ?: usage.optJSONArray("candidatesTokensDetails")
            ?: usage.optJSONArray("candidates_tokens_details")

        var audioIn = 0
        var audioOut = 0
        var text = 0
        promptDetails?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val count = item.optInt("tokenCount", item.optInt("token_count"))
                when (item.optString("modality").uppercase()) {
                    "AUDIO" -> audioIn += count
                    "TEXT" -> text += count
                }
            }
        }
        responseDetails?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val count = item.optInt("tokenCount", item.optInt("token_count"))
                when (item.optString("modality").uppercase()) {
                    "AUDIO" -> audioOut += count
                    "TEXT" -> text += count
                }
            }
        }

        return UsageSample(
            promptTokens = prompt,
            responseTokens = response,
            totalTokens = if (total > 0) total else prompt + response,
            thoughtTokens = thoughts,
            cachedTokens = cached,
            audioInTokens = audioIn,
            audioOutTokens = audioOut,
            textTokens = text
        )
    }

    /** google.rpc.RetryInfo ("49s") or the retry hint inside the message text. */
    private fun retryDelayOf(error: org.json.JSONObject, detail: String?): Long {
        val details = error.optJSONArray("details")
        if (details != null) {
            for (i in 0 until details.length()) {
                val item = details.optJSONObject(i) ?: continue
                val delay = item.optString("retryDelay").ifEmpty { item.optString("retry_delay") }
                secondsToMs(delay)?.let { return it }
            }
        }
        val match = Regex("retry in (\\d+(?:\\.\\d+)?)s").find(detail.orEmpty())
        return match?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 0L
    }

    private fun rateOf(mime: String?): Int {
        val match = Regex("rate=(\\d+)").find(mime ?: "") ?: return DEFAULT_OUTPUT_RATE
        return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_OUTPUT_RATE
    }

    private fun secondsToMs(value: String?): Long? {
        val match = Regex("^(\\d+(?:\\.\\d+)?)s$").find(value?.trim().orEmpty()) ?: return null
        return (match.groupValues[1].toDouble() * 1000).toLong()
    }

    private fun scheduleReconnect(delayMs: Long) {
        if (closed || reconnectScheduled) return
        reconnectScheduled = true
        handler.postDelayed({
            reconnectScheduled = false
            reconnectNow()
        }, maxOf(250L, delayMs))
    }

    private fun reconnectNow() {
        if (closed) return
        val previous = socket
        socket = null
        ready = false
        generation += 1
        try {
            previous?.close(1000, "reconnect")
        } catch (_: Throwable) {
        }
        openSocket()
    }

    fun close() {
        handler.post {
            closed = true
            ready = false
            generation += 1
            try {
                socket?.close(1000, "session finished")
            } catch (_: Throwable) {
            }
            socket = null
            pending.clear()
            http.dispatcher.executorService.shutdown()
            thread.quitSafely()
        }
    }
}
