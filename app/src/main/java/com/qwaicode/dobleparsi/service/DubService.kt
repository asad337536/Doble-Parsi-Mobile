/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Made in the open by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.qwaicode.dobleparsi.MainActivity
import com.qwaicode.dobleparsi.R
import com.qwaicode.dobleparsi.audio.DubPlayer
import com.qwaicode.dobleparsi.audio.DuckController
import com.qwaicode.dobleparsi.audio.FloatRingBuffer
import com.qwaicode.dobleparsi.audio.Int16StreamReader
import com.qwaicode.dobleparsi.audio.Pcm
import com.qwaicode.dobleparsi.audio.PlaybackCapture
import com.qwaicode.dobleparsi.audio.SincResampler
import com.qwaicode.dobleparsi.captions.CaptionTrack
import com.qwaicode.dobleparsi.captions.Subtitles
import com.qwaicode.dobleparsi.core.Loc
import com.qwaicode.dobleparsi.core.Output
import com.qwaicode.dobleparsi.core.Settings
import com.qwaicode.dobleparsi.core.SettingsStore
import com.qwaicode.dobleparsi.core.UsageSample
import com.qwaicode.dobleparsi.core.UsageStore
import com.qwaicode.dobleparsi.media.MediaTitleWatcher
import com.qwaicode.dobleparsi.net.GeminiLiveClient
import com.qwaicode.dobleparsi.overlay.BubbleArm
import com.qwaicode.dobleparsi.overlay.BubbleView
import com.qwaicode.dobleparsi.overlay.OverlayBubble
import com.qwaicode.dobleparsi.record.AacRecorder

/**
 * The whole session in one foreground service:
 * playback capture -> 16 kHz PCM -> Gemini Live -> dub playback + .m4a + subtitles.
 *
 * Built by QW-AI-Code — https://github.com/QW-AI-Code
 */
class DubService : Service() {

    companion object {
        const val ACTION_START = "com.qwaicode.dobleparsi.START"
        const val ACTION_STOP = "com.qwaicode.dobleparsi.STOP"
        const val ACTION_SHOW_BUBBLE = "com.qwaicode.dobleparsi.SHOW_BUBBLE"
        const val ACTION_DISMISS_BUBBLE = "com.qwaicode.dobleparsi.DISMISS_BUBBLE"
        const val ACTION_SYNC_SETTINGS = "com.qwaicode.dobleparsi.SYNC_SETTINGS"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        private const val CHANNEL_ID = "dubbing"
        private const val CHANNEL_ID_SAVED = "dubbing-saved"
        private const val NOTIFICATION_ID = 4211
        private const val NOTIFICATION_ID_SAVED = 4212

        private const val API_INPUT_RATE = 16000
        private const val SEND_CHUNK_FRAMES = 1600      // ~100 ms
        private const val TICK_MS = 250L
        private const val MAX_DRAIN_MS = 6_000L
        private const val ENCODER_STOP_MS = 5_000L
        private const val SHUTDOWN_WATCHDOG_MS = 25_000L
        private const val MIX_DUB_GAIN = 0.92f
        private const val LEVEL_POST_MS = 60L
        private const val PREBUFFER_FAST_MS = 60
        private const val PREBUFFER_SAFE_MS = 180

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, DubService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            send(context, ACTION_STOP)
        }

        /** Brings the floating control back, e.g. after the overlay permission was granted. */
        fun showBubble(context: Context) {
            send(context, ACTION_SHOW_BUBBLE)
        }

        fun dismissBubble(context: Context) {
            send(context, ACTION_DISMISS_BUBBLE)
        }

        /** Pushes changed settings into a live/standby session (bubble look, timer…). */
        fun syncSettings(context: Context) {
            send(context, ACTION_SYNC_SETTINGS)
        }

        private fun send(context: Context, action: String) {
            val intent = Intent(context, DubService::class.java).apply { this.action = action }
            runCatching { context.startService(intent) }
        }
    }

    private var settings: Settings = Settings()
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private lateinit var mainHandler: Handler

    private var projection: MediaProjection? = null
    private var capture: PlaybackCapture? = null
    private var player: DubPlayer? = null
    private var client: GeminiLiveClient? = null
    private var duck: DuckController? = null
    private var bubble: OverlayBubble? = null
    private var recorder: AacRecorder? = null
    private var pendingAudio: Output.PendingFile? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var titles: MediaTitleWatcher? = null
    private lateinit var usage: UsageStore
    /** True while the service stays alive only to keep the bubble on screen. */
    private var standby = false
    private var lastLevelAt = 0L

    private var apiResampler: SincResampler? = null
    private var dubResampler: SincResampler? = null
    private var dubRate = 0
    private val dubReader = Int16StreamReader()
    private val dubFifo = FloatRingBuffer(1 shl 19)

    private var dubCaptions = CaptionTrack()
    private var sourceCaptions = CaptionTrack()

    private var sendBuffer = FloatArray(SEND_CHUNK_FRAMES * 4)
    private var sendCount = 0
    private var startedAt = 0L
    private var sessionName = ""
    private var stopping = false
    private var teardownDone = false

    private val ticker = object : Runnable {
        override fun run() {
            val now = elapsedMs()
            dubCaptions.tick(now)
            sourceCaptions.tick(now)
            SessionState.update { it.copy(elapsedMs = now) }
            bubble?.setElapsed(now)
            if (!stopping) handler.postDelayed(this, TICK_MS)
        }
    }

    /**
     * Last line of defence for the "Saving output…" notification that used to stay
     * on screen forever: whatever happens while finishing, the foreground
     * notification is torn down and the service dies.
     */
    private val watchdog = Runnable { forceTeardown() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("dub-session").apply { start() }
        handler = Handler(worker.looper)
        mainHandler = Handler(mainLooper)
        settings = SettingsStore.get(this).value
        usage = UsageStore.get(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                if (data == null) {
                    fail("err.capture", null)
                    return START_NOT_STICKY
                }
                standby = false
                stopping = false
                teardownDone = false
                goForeground()
                handler.post { begin(code, data) }
            }
            ACTION_STOP -> handler.post { finish() }
            ACTION_SHOW_BUBBLE -> {
                settings = SettingsStore.get(this).refresh()
                if (BubbleArm.isArmed(this)) ensureBubble()
                if (bubble == null && !SessionState.isRunning) forceTeardown()
            }
            ACTION_DISMISS_BUBBLE -> {
                BubbleArm.disarm(this)
                bubble?.hide()
                bubble = null
                if (standby || !SessionState.isRunning) forceTeardown()
            }
            ACTION_SYNC_SETTINGS -> {
                settings = SettingsStore.get(this).refresh()
                if (settings.showBubble && BubbleArm.isArmed(this)) {
                    ensureBubble()
                } else if (!SessionState.isRunning) {
                    bubble?.hide()
                    bubble = null
                }
                bubble?.applySettings(settings)
                if (bubble == null && !SessionState.isRunning) forceTeardown()
            }
            null -> if (!SessionState.isRunning && !standby) forceTeardown()
        }
        return START_NOT_STICKY
    }

    private fun strings(): Resources = Loc.resources(this, settings.uiLang)

    private fun text(id: Int): String = strings().getString(id)

    // ── session ─────────────────────────────────────────────────────────────

    private fun begin(resultCode: Int, data: Intent) {
        settings = SettingsStore.get(this).refresh()
        dubCaptions = CaptionTrack()
        sourceCaptions = CaptionTrack()
        dubFifo.clear()
        dubReader.reset()
        dubRate = 0
        sendCount = 0
        lastLevelAt = 0L

        if (settings.apiKey.isBlank()) {
            fail("err.noKey", null)
            return
        }
        if (!settings.livePlayback && !settings.saveAudio && !settings.saveSubtitle) {
            fail("err.noOutput", null)
            return
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val active = try {
            manager.getMediaProjection(resultCode, data)
        } catch (_: Throwable) {
            null
        }
        if (active == null) {
            fail("err.capture", null)
            return
        }
        projection = active
        active.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { finish() }
            }
        }, handler)

        startedAt = SystemClock.elapsedRealtime()

        // Watch the media session for the whole run, not just at start.
        val watcher = MediaTitleWatcher(this).also { it.start(handler) }
        titles = watcher
        sessionName = Output.sessionName(
            rawTitle = watcher.bestTitle(),
            playerLabel = watcher.playerLabel(),
            fallbackLabel = text(R.string.app_name)
        )

        SessionState.update { DubUiState(phase = Phase.CONNECTING, sessionName = sessionName) }

        acquireWakeLock()

        val capturer = PlaybackCapture(active)
        val started = capturer.start { mono, frames -> onCaptured(mono, frames) }
        if (!started) {
            fail("err.capture", null)
            return
        }
        capture = capturer

        val rate = capturer.sampleRate
        apiResampler = SincResampler(rate, API_INPUT_RATE)

        val dubPlayer = DubPlayer(
            sampleRate = rate,
            prebufferMs = if (settings.lowLatency) PREBUFFER_FAST_MS else PREBUFFER_SAFE_MS,
            onActiveChanged = { speaking -> onSpeaking(speaking) },
            onPlayed = { samples, count ->
                dubFifo.write(samples, count)
                meter(samples, count)
            }
        )
        dubPlayer.setVolume(if (settings.livePlayback) settings.dubVolume else 0f)
        dubPlayer.start()
        // 0 ms by default: the dub is heard the instant the model starts speaking.
        if (settings.dubSyncMs > 0) dubPlayer.pushSilence(settings.dubSyncMs)
        player = dubPlayer

        duck = DuckController(this)
        // "Keep the original quiet": hold the duck down for the whole session
        // instead of only while the dub voice is mid sentence.
        if (settings.livePlayback && settings.duckOriginal && settings.strongDuck) {
            duck?.duck()
        }

        if (settings.saveAudio) {
            val pending = Output.createAudio(this, sessionName)
            if (pending != null) {
                pendingAudio = pending
                recorder = try {
                    AacRecorder(pending.descriptor.fileDescriptor, rate)
                } catch (_: Throwable) {
                    null
                }
            }
        }

        client = GeminiLiveClient(
            apiKey = settings.apiKey,
            model = settings.translateModel,
            targetLanguageCode = settings.targetLang,
            sourceTranscription = settings.saveSubtitle && settings.bilingualSubtitle,
            dubTranscription = settings.saveSubtitle,
            listener = liveListener
        ).also { it.connect() }

        usage.startSession()

        if (settings.showBubble) {
            BubbleArm.arm(this)
            ensureBubble()
        }
        bubble?.setElapsed(0)
        bubble?.setMode(BubbleView.Mode.CONNECTING)

        handler.postDelayed(ticker, TICK_MS)
        updateNotification()
    }

    private val liveListener = object : GeminiLiveClient.Listener {

        override fun onReady() {
            SessionState.update { it.copy(phase = Phase.LIVE, errorCode = null) }
            usage.clearQuota()
            bubble?.setMode(BubbleView.Mode.LIVE)
            updateNotification()
        }

        override fun onAudio(pcm: ByteArray, sampleRate: Int) {
            val target = player ?: return
            if (dubRate != sampleRate) {
                dubRate = sampleRate
                dubResampler = SincResampler(sampleRate, capture?.sampleRate ?: 48000)
                dubReader.reset()
            }
            val floats = dubReader.push(pcm)
            if (floats.isEmpty()) return
            val resampled = dubResampler?.process(floats) ?: floats
            target.push(resampled)
        }

        override fun onSourceText(text: String) {
            val now = elapsedMs()
            if (settings.bilingualSubtitle) sourceCaptions.push(text, now)
            SessionState.update { it.copy(sourceText = trimTail(it.sourceText + text)) }
        }

        override fun onDubText(text: String) {
            dubCaptions.push(text, elapsedMs())
            SessionState.update { it.copy(dubText = trimTail(it.dubText + text)) }
        }

        override fun onInterrupted() {
            dubReader.reset()
            player?.reset()
        }

        override fun onError(code: String, detail: String?) {
            SessionState.update { it.copy(errorCode = code, errorDetail = detail) }
        }

        override fun onUsage(sample: UsageSample) {
            usage.record(sample)
        }

        override fun onQuota(message: String?, retryMs: Long) {
            usage.noteQuota(message, retryMs)
        }
    }

    /** Capture thread: feeds the model and the recorder. */
    private fun onCaptured(mono: FloatArray, frames: Int) {
        if (stopping) return

        // 1) model input: resample to 16 kHz and ship ~100 ms chunks
        val resampled = apiResampler?.process(mono, frames)
        if (resampled != null && resampled.isNotEmpty()) {
            if (sendCount + resampled.size > sendBuffer.size) {
                sendBuffer = sendBuffer.copyOf(maxOf(sendBuffer.size * 2, sendCount + resampled.size))
            }
            resampled.copyInto(sendBuffer, sendCount)
            sendCount += resampled.size

            while (sendCount >= SEND_CHUNK_FRAMES) {
                val bytes = ByteArray(SEND_CHUNK_FRAMES * 2)
                for (i in 0 until SEND_CHUNK_FRAMES) {
                    val sample = sendBuffer[i]
                    val value = when {
                        sample >= 1f -> 32767
                        sample <= -1f -> -32768
                        else -> Math.round(sample * 32767f)
                    }
                    bytes[i * 2] = (value and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
                }
                client?.sendAudio(bytes)
                sendBuffer.copyInto(sendBuffer, 0, SEND_CHUNK_FRAMES, sendCount)
                sendCount -= SEND_CHUNK_FRAMES
            }
        }

        // 2) recording: mix the dub that was actually played with the original
        val target = recorder ?: return
        if (mixBuffer.size < frames) {
            mixBuffer = FloatArray(frames)
            dubBlock = FloatArray(frames)
            mixShorts = ShortArray(frames)
        }
        dubFifo.read(dubBlock, frames)
        val wantsMix = settings.audioSource == "mix"
        val originalGain = settings.originalLevel.coerceIn(0f, 1f)
        for (i in 0 until frames) {
            val dub = dubBlock[i] * MIX_DUB_GAIN
            mixBuffer[i] = if (wantsMix) dub + mono[i] * originalGain else dub
        }
        Pcm.softLimitInPlace(mixBuffer, frames)
        Pcm.floatToShort(mixBuffer, frames, mixShorts)
        target.feed(mixShorts, frames)
    }

    private var mixBuffer = FloatArray(0)
    private var dubBlock = FloatArray(0)
    private var mixShorts = ShortArray(0)

    private fun onSpeaking(speaking: Boolean) {
        SessionState.update { it.copy(speaking = speaking) }
        bubble?.setSpeaking(speaking)
        if (!speaking) bubble?.setLevel(0f)
        val controller = duck ?: return
        if (!settings.duckOriginal || !settings.livePlayback) return
        // strongDuck keeps the original low for the whole session.
        if (speaking) controller.duck() else if (!settings.strongDuck) controller.scheduleRelease()
    }

    /** Feeds the bubble the real loudness of the dub so the animation matches the voice. */
    private fun meter(samples: FloatArray, count: Int) {
        if (count <= 0) return
        val target = bubble ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLevelAt < LEVEL_POST_MS) return
        lastLevelAt = now
        var sum = 0f
        var index = 0
        while (index < count) {
            val value = samples[index]
            sum += value * value
            index += 4
        }
        val rms = kotlin.math.sqrt(sum / maxOf(1, count / 4))
        target.setLevel((rms * 3.4f).coerceIn(0f, 1f))
    }

    // -- bubble ------------------------------------------------------------

    private fun ensureBubble() {
        if (!settings.showBubble) return
        val existing = bubble
        if (existing != null) {
            if (!existing.isShowing) existing.show()
            existing.applySettings(settings)
            return
        }
        val created = OverlayBubble(
            context = this,
            onTap = { onBubbleTap() },
            onLongPress = { onBubbleLongPress() }
        )
        if (!created.canShow()) return
        created.applySettings(settings)
        created.show()
        created.setMode(if (SessionState.isRunning) BubbleView.Mode.LIVE else BubbleView.Mode.IDLE)
        bubble = created
    }

    /** Running: stop. Idle: start the next dub straight from the bubble. */
    private fun onBubbleTap() {
        if (SessionState.isRunning) {
            handler.post { finish() }
        } else {
            requestStartFromBubble()
        }
    }

    private fun onBubbleLongPress() {
        BubbleArm.disarm(this)
        bubble?.hide()
        bubble = null
        if (!SessionState.isRunning) forceTeardown()
    }

    /**
     * Android hands out a one-shot MediaProjection consent per capture, so a new dub
     * has to go through the activity. It opens, asks, and starts without any extra tap.
     */
    private fun requestStartFromBubble() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(MainActivity.EXTRA_QUICK_START, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { startActivity(intent) }
    }

    // ── shutdown ────────────────────────────────────────────────────────────

    private fun finish() {
        if (stopping) return
        stopping = true
        handler.removeCallbacks(ticker)
        mainHandler.postDelayed(watchdog, SHUTDOWN_WATCHDOG_MS)

        SessionState.update { it.copy(phase = Phase.FINISHING, speaking = false) }
        bubble?.setMode(BubbleView.Mode.FINISHING)
        updateNotification()

        var saved: List<String> = emptyList()
        try {
            saved = closeEverythingAndSave()
        } catch (_: Throwable) {
            // never let an exception strand the notification
        } finally {
            SessionState.update {
                it.copy(phase = Phase.IDLE, speaking = false, savedFiles = saved, elapsedMs = 0)
            }
            mainHandler.removeCallbacks(watchdog)
            publishSavedNotification(saved)
            if (keepBubbleAlive()) enterStandby() else forceTeardown()
        }
    }

    /** The bubble outlives the session when the user asked it to. */
    private fun keepBubbleAlive(): Boolean =
        settings.showBubble && settings.keepBubble &&
            BubbleArm.isArmed(this) && bubble?.isShowing == true

    /**
     * Session over, bubble stays: the notification and every audio object are gone,
     * the service simply parks so the next tap on the bubble can start a new dub.
     */
    private fun enterStandby() {
        standby = true
        stopping = false
        teardownDone = false
        startedAt = 0L
        sessionName = ""
        sendCount = 0
        lastLevelAt = 0L
        dubRate = 0
        dubFifo.clear()
        dubReader.reset()
        apiResampler = null
        dubResampler = null
        handler.removeCallbacks(ticker)
        clearNotification()
        settings = SettingsStore.get(this).refresh()
        bubble?.applySettings(settings)
        bubble?.setLevel(0f)
        bubble?.setElapsed(0)
        bubble?.setMode(BubbleView.Mode.IDLE)
    }

    /** Everything that can block, with hard time limits on each step. */
    private fun closeEverythingAndSave(): List<String> {
        capture?.stop()
        capture = null

        // let the dub tail play out before tearing the socket down
        val current = player
        current?.drain()
        val deadline = SystemClock.elapsedRealtime() + MAX_DRAIN_MS
        while (current != null && !current.isDrained() && SystemClock.elapsedRealtime() < deadline) {
            try {
                Thread.sleep(60)
            } catch (_: InterruptedException) {
                break
            }
        }

        runCatching { client?.close() }
        client = null
        runCatching { current?.stop() }
        player = null

        runCatching { duck?.abandon() }
        duck = null
        if (!keepBubbleAlive()) {
            bubble?.hide()
            bubble = null
        }

        // The real title is resolved now, not at start: this is what makes the
        // outputs carry the video name instead of a timestamp.
        titles?.sample()
        val resolvedTitle = Output.cleanTitle(titles?.bestTitle())
        titles?.stop()
        titles = null
        val finalName = when {
            resolvedTitle.isNotEmpty() && resolvedTitle != sessionName &&
                Output.isFallbackName(sessionName) -> resolvedTitle
            resolvedTitle.isNotEmpty() && sessionName.isBlank() -> resolvedTitle
            else -> sessionName
        }

        val saved = ArrayList<String>()

        recorder?.let { active ->
            stopEncoder(active)
            pendingAudio?.let { pending ->
                Output.publish(this, pending.uri)
                runCatching { pending.descriptor.close() }
                if (active.hasAudio) {
                    if (finalName != sessionName) {
                        renameAudio(pending.uri, finalName)
                    }
                    saved.add("$finalName.m4a")
                }
            }
        }
        recorder = null
        pendingAudio = null
        sessionName = finalName
        SessionState.update { it.copy(sessionName = finalName) }

        if (settings.saveSubtitle) {
            val cues = dubCaptions.finalize(settings.subtitleOffsetMs)
            if (cues.isNotEmpty()) {
                val format = settings.subtitleFormat
                if (format == "srt" || format == "both") {
                    Output.writeText(
                        this, finalName, "$finalName.srt",
                        "application/x-subrip", Subtitles.toSrt(cues)
                    )?.let { saved.add("$finalName.srt") }
                }
                if (format == "vtt" || format == "both") {
                    Output.writeText(
                        this, finalName, "$finalName.vtt",
                        "text/vtt", Subtitles.toVtt(cues)
                    )?.let { saved.add("$finalName.vtt") }
                }
            }
            if (settings.bilingualSubtitle) {
                val original = sourceCaptions.finalize(0)
                if (original.isNotEmpty()) {
                    Output.writeText(
                        this, finalName, "$finalName.original.srt",
                        "application/x-subrip", Subtitles.toSrt(original)
                    )?.let { saved.add("$finalName.original.srt") }
                }
            }
        }

        runCatching { projection?.stop() }
        projection = null
        releaseWakeLock()
        return saved
    }

    private fun renameAudio(uri: Uri, finalName: String) {
        runCatching { Output.rename(this, uri, finalName, "$finalName.m4a") }
    }

    /** MediaCodec/MediaMuxer can hang on bad OEM encoders; give it a deadline. */
    private fun stopEncoder(active: AacRecorder) {
        val closer = Thread({ runCatching { active.finish() } }, "dub-encoder-stop")
        closer.isDaemon = true
        closer.start()
        runCatching { closer.join(ENCODER_STOP_MS) }
    }

    override fun onDestroy() {
        titles?.stop()
        titles = null
        mainHandler.removeCallbacks(watchdog)
        if (!stopping) {
            stopping = true
            runCatching { capture?.stop() }
            runCatching { client?.close() }
            runCatching { player?.stop() }
            runCatching { duck?.abandon() }
            bubble?.hide()
            bubble = null
            releaseWakeLock()
            SessionState.update { it.copy(phase = Phase.IDLE, speaking = false) }
        }
        clearNotification()
        worker.quitSafely()
        super.onDestroy()
    }

    private fun fail(code: String, detail: String?) {
        SessionState.update {
            it.copy(phase = Phase.IDLE, speaking = false, errorCode = code, errorDetail = detail)
        }
        bubble?.hide()
        bubble = null
        titles?.stop()
        titles = null
        releaseWakeLock()
        forceTeardown()
    }

    private fun forceTeardown() {
        if (teardownDone) return
        teardownDone = true
        clearNotification()
        stopSelf()
    }

    private fun clearNotification() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        // stopForeground alone can leave a stale card on some OEM shells.
        runCatching { getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
    }

    // ── plumbing ────────────────────────────────────────────────────────────

    private fun elapsedMs(): Long =
        if (startedAt == 0L) 0L else maxOf(0L, SystemClock.elapsedRealtime() - startedAt)

    private fun trimTail(text: String): String =
        if (text.length <= 1200) text else text.takeLast(1200)

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val session = NotificationChannel(
            CHANNEL_ID,
            text(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableVibration(false)
        }
        val done = NotificationChannel(
            CHANNEL_ID_SAVED,
            text(R.string.notification_channel_saved),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(session)
        manager.createNotificationChannel(done)
    }

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, DubService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val phase = SessionState.state.value.phase
        val finishing = phase == Phase.FINISHING
        val title = if (finishing) text(R.string.notification_saving) else text(R.string.notification_title)
        val body = when (phase) {
            Phase.CONNECTING -> text(R.string.status_connecting)
            Phase.FINISHING -> sessionName.ifBlank { text(R.string.status_finishing) }
            else -> sessionName.ifBlank { text(R.string.status_live) }
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (finishing) {
            // A real progress bar instead of a frozen "Saving output…" line.
            builder.setProgress(0, 0, true)
        } else {
            builder.addAction(R.drawable.ic_stop, text(R.string.notification_stop), stop)
        }
        return builder.build()
    }

    /** Short, dismissible confirmation once the files are actually on disk. */
    private fun publishSavedNotification(saved: List<String>) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val body = if (saved.isEmpty()) {
            text(R.string.notification_saved_none)
        } else {
            "${text(R.string.saved_files)}\n" + saved.joinToString("\n")
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SAVED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (saved.isEmpty()) text(R.string.notification_saved_none)
                else text(R.string.notification_saved_title)
            )
            .setContentText(if (saved.isEmpty()) "" else saved.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(false)
            .setAutoCancel(true)
            .setSilent(true)
            .setTimeoutAfter(60_000L)
            .setContentIntent(openIntent())
            .build()
        runCatching { manager.notify(NOTIFICATION_ID_SAVED, notification) }
    }

    private fun goForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun updateNotification() {
        if (teardownDone) return
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun acquireWakeLock() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DobleParsi:session").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }
}
