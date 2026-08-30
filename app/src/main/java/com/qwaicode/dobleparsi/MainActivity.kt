/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * @QW-AI-Code on GitHub
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.qwaicode.dobleparsi.core.Loc
import com.qwaicode.dobleparsi.core.SettingsStore
import com.qwaicode.dobleparsi.core.UsageStore
import com.qwaicode.dobleparsi.media.MediaTitle
import com.qwaicode.dobleparsi.overlay.BubbleArm
import com.qwaicode.dobleparsi.service.DubService
import com.qwaicode.dobleparsi.service.SessionState
import com.qwaicode.dobleparsi.ui.AppLocale
import com.qwaicode.dobleparsi.ui.DobleParsiTheme
import com.qwaicode.dobleparsi.ui.DubScreen

/**
 * Doble Parsi - live AI dubbing for anything playing on the phone.
 * Built by QW-AI-Code - https://github.com/QW-AI-Code
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** Set by the floating bubble: open, ask for capture consent, start at once. */
        const val EXTRA_QUICK_START = "com.qwaicode.dobleparsi.QUICK_START"
    }

    private lateinit var store: SettingsStore
    private lateinit var usage: UsageStore

    /** Bumped every time the bubble asks for a new session. */
    private var quickStart by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        store = SettingsStore.get(this)
        usage = UsageStore.get(this)
        applyWindowDirection(store.value.uiLang)
        consumeQuickStart(intent)

        setContent {
            val settings by store.state.collectAsState()
            val state by SessionState.state.collectAsState()
            val usageSnapshot by usage.state.collectAsState()

            DobleParsiTheme {
                AppLocale(settings.uiLang) {
                    var needsNotificationAccess by remember {
                        mutableStateOf(!MediaTitle.isEnabled(this))
                    }

                    // The permission is granted in system Settings, so re-check it
                    // every time the user comes back to the app.
                    val lifecycleOwner = this@MainActivity
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                needsNotificationAccess = !MediaTitle.isEnabled(this@MainActivity)
                                restoreBubbleIfNeeded()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    LaunchedEffect(settings.uiLang) { applyWindowDirection(settings.uiLang) }

                    val projectionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        val data = result.data
                        if (result.resultCode == RESULT_OK && data != null) {
                            DubService.start(this, result.resultCode, data)
                        } else {
                            SessionState.update { it.copy(errorCode = "err.capture") }
                        }
                    }

                    val micLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) requestProjection(projectionLauncher::launch)
                    }

                    val notificationsLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { }

                    val beginSession: () -> Unit = {
                        SessionState.update { it.copy(errorCode = null, savedFiles = emptyList()) }
                        when {
                            settings.apiKey.isBlank() ->
                                SessionState.update { it.copy(errorCode = "err.noKey") }

                            !settings.livePlayback && !settings.saveAudio && !settings.saveSubtitle ->
                                SessionState.update { it.copy(errorCode = "err.noOutput") }

                            else -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                if (settings.showBubble && !AndroidSettings.canDrawOverlays(this)) {
                                    runCatching {
                                        startActivity(
                                            Intent(
                                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:$packageName")
                                            )
                                        )
                                    }
                                }
                                if (ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.RECORD_AUDIO
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    requestProjection(projectionLauncher::launch)
                                }
                            }
                        }
                    }

                    // Tapping the idle bubble lands here: no extra tap in the app.
                    LaunchedEffect(quickStart) {
                        if (quickStart > 0 && !SessionState.isRunning) beginSession()
                    }

                    DubScreen(
                        settings = settings,
                        state = state,
                        usage = usageSnapshot,
                        needsNotificationAccess = needsNotificationAccess && settings.useOriginalFilename,
                        onSettingsChange = { transform ->
                            store.update(transform)
                            // Only wake the service when there is something to update.
                            if (SessionState.isRunning || BubbleArm.isArmed(this)) {
                                DubService.syncSettings(this)
                            }
                        },
                        onToggleUiLang = {
                            val next = if (settings.uiLang == "fa") "en" else "fa"
                            store.update { it.copy(uiLang = next) }
                            applyWindowDirection(next)
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(next)
                            )
                            if (SessionState.isRunning || BubbleArm.isArmed(this)) {
                                DubService.syncSettings(this)
                            }
                        },
                        onStart = beginSession,
                        onStop = { DubService.stop(this) },
                        onOpenNotificationAccess = {
                            runCatching {
                                startActivity(
                                    Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            }
                        },
                        onClearUsage = { usage.clearAll() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeQuickStart(intent)
    }

    /** One shot: the extra is stripped so a rotation cannot start a second session. */
    private fun consumeQuickStart(source: Intent?) {
        val intent = source ?: return
        if (intent.getBooleanExtra(EXTRA_QUICK_START, false)) {
            intent.removeExtra(EXTRA_QUICK_START)
            quickStart += 1
        }
    }

    /**
     * The bubble is meant to survive sessions, so whenever the app is opened and the
     * user has armed it, make sure it is back on screen.
     */
    private fun restoreBubbleIfNeeded() {
        val settings = store.value
        if (!settings.showBubble || !settings.keepBubble) return
        if (!BubbleArm.isArmed(this)) return
        if (!AndroidSettings.canDrawOverlays(this)) return
        DubService.showBubble(this)
    }

    /** Keeps the window itself in sync with the chosen UI language. */
    private fun applyWindowDirection(lang: String) {
        val direction = if (Loc.isRtl(lang)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        window.decorView.layoutDirection = direction
        findViewById<View>(android.R.id.content)?.layoutDirection = direction
    }

    private fun requestProjection(launch: (Intent) -> Unit) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launch(manager.createScreenCaptureIntent())
    }
}
