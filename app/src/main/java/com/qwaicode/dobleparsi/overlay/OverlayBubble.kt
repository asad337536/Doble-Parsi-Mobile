/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Crafted by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.qwaicode.dobleparsi.core.Loc
import com.qwaicode.dobleparsi.core.Settings as AppSettings
import kotlin.math.abs

/**
 * Window host for the floating control.
 *
 * The bubble is a full session control now, not just a stop button: tap it to stop a
 * running dub, tap it again to start the next one without going back into the app,
 * drag it anywhere (it snaps to the nearest edge), and long press to dismiss it.
 * While idle it can fade down and collapse into an edge tab so it never covers the
 * video, and it wakes up on its own the moment a session starts.
 *
 * Every window/view call is marshalled to the main thread because the session
 * service drives this from its own worker thread.
 */
class OverlayBubble(
    private val context: Context,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit = {}
) {

    private companion object {
        const val PREFS = "doble_parsi_bubble"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val IDLE_DELAY_MS = 4_000L
        const val LONG_PRESS_MS = 550L
        const val DRAG_SLOP_DP = 10f
        const val EDGE_MARGIN_DP = 6f
        const val SNAP_STEP_MS = 16L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    private var view: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var settings: AppSettings = AppSettings()
    private var lastMode: BubbleView.Mode = BubbleView.Mode.IDLE

    private val idleAction = Runnable { relax() }

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    val isShowing: Boolean get() = view != null

    // -- lifecycle ---------------------------------------------------------

    fun show() = onUi {
        if (view != null || !canShow()) return@onUi

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, dp(EDGE_MARGIN_DP).toInt())
            y = prefs.getInt(KEY_Y, (screenHeight() * 0.42f).toInt())
        }
        params = layout

        val bubble = BubbleView(context).apply {
            mode = lastMode
            showTimer = settings.bubbleTimer
            scaleFactor = settings.bubbleScale
            locale = Loc.locale(settings.uiLang)
            dockedLeft = layout.x < screenWidth() / 2
        }
        attachTouch(bubble)

        try {
            windowManager.addView(bubble, layout)
            view = bubble
            wake()
        } catch (_: Throwable) {
            view = null
            params = null
        }
    }

    fun hide() = onUi {
        val current = view ?: return@onUi
        ui.removeCallbacks(idleAction)
        savePosition()
        view = null
        params = null
        runCatching { windowManager.removeView(current) }
    }

    // -- state from the session --------------------------------------------

    fun setMode(mode: BubbleView.Mode) = onUi {
        lastMode = mode
        view?.mode = mode
        if (mode == BubbleView.Mode.IDLE) scheduleRelax() else wake()
    }

    fun setSpeaking(speaking: Boolean) = onUi {
        if (lastMode == BubbleView.Mode.IDLE || lastMode == BubbleView.Mode.FINISHING) return@onUi
        val next = if (speaking) BubbleView.Mode.SPEAKING else BubbleView.Mode.LIVE
        lastMode = next
        view?.mode = next
    }

    fun setLevel(level: Float) = onUi {
        view?.level = level
    }

    fun setElapsed(ms: Long) = onUi {
        view?.elapsedMs = ms
    }

    fun applySettings(next: AppSettings) = onUi {
        settings = next
        val bubble = view ?: return@onUi
        bubble.showTimer = next.bubbleTimer
        bubble.scaleFactor = next.bubbleScale
        bubble.locale = Loc.locale(next.uiLang)
        if (!next.bubbleFade) bubble.alpha = 1f
        if (!next.bubbleCollapse && bubble.collapsed) expand()
        if (lastMode == BubbleView.Mode.IDLE) scheduleRelax() else wake()
    }

    // -- idle behaviour ----------------------------------------------------

    /** Full size, full opacity: the bubble is in use. */
    private fun wake() {
        ui.removeCallbacks(idleAction)
        val bubble = view ?: return
        bubble.alpha = 1f
        if (bubble.collapsed) expand()
    }

    private fun scheduleRelax() {
        ui.removeCallbacks(idleAction)
        if (!settings.bubbleFade && !settings.bubbleCollapse) return
        ui.postDelayed(idleAction, if (settings.bubbleSmart) IDLE_DELAY_MS else IDLE_DELAY_MS * 2)
    }

    /** Idle: dim down and/or shrink into the edge tab. */
    private fun relax() {
        val bubble = view ?: return
        if (lastMode != BubbleView.Mode.IDLE) return
        if (settings.bubbleFade) bubble.alpha = 0.45f
        if (settings.bubbleCollapse) collapse()
    }

    private fun collapse() {
        val bubble = view ?: return
        val layout = params ?: return
        if (bubble.collapsed) return
        val toLeft = layout.x + bubble.width / 2 < screenWidth() / 2
        bubble.dockedLeft = toLeft
        bubble.collapsed = true
        layout.x = if (toLeft) 0 else screenWidth() - (dp(20f) * settings.bubbleScale).toInt()
        applyLayout()
    }

    private fun expand() {
        val bubble = view ?: return
        val layout = params ?: return
        if (!bubble.collapsed) return
        bubble.collapsed = false
        val size = (dp(66f) * settings.bubbleScale).toInt()
        layout.x = if (bubble.dockedLeft) {
            dp(EDGE_MARGIN_DP).toInt()
        } else {
            screenWidth() - size - dp(EDGE_MARGIN_DP).toInt()
        }
        applyLayout()
    }

    // -- touch -------------------------------------------------------------

    private fun attachTouch(target: BubbleView) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        var downAt = 0L
        var longFired = false
        val slop = dp(DRAG_SLOP_DP)

        val longPress = Runnable {
            longFired = true
            onLongPress()
        }

        target.setOnTouchListener { _, event ->
            val layout = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x
                    startY = layout.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    longFired = false
                    downAt = SystemClock.elapsedRealtime()
                    wake()
                    ui.postDelayed(longPress, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) {
                        if (!moved) ui.removeCallbacks(longPress)
                        moved = true
                    }
                    if (moved) {
                        layout.x = startX + dx
                        layout.y = clampY(startY + dy)
                        applyLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    ui.removeCallbacks(longPress)
                    val quick = SystemClock.elapsedRealtime() - downAt < LONG_PRESS_MS
                    if (!moved && !longFired && quick) {
                        onTap()
                    } else if (moved) {
                        savePosition()
                        if (settings.bubbleSnap) snapToEdge() else clampIntoScreen()
                    }
                    if (lastMode == BubbleView.Mode.IDLE) scheduleRelax()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(longPress)
                    if (lastMode == BubbleView.Mode.IDLE) scheduleRelax()
                    true
                }
                else -> false
            }
        }
    }

    // -- geometry ----------------------------------------------------------

    /** Slides the bubble to the closest edge instead of leaving it mid screen. */
    private fun snapToEdge() {
        val bubble = view ?: return
        val layout = params ?: return
        val margin = dp(EDGE_MARGIN_DP).toInt()
        val width = if (bubble.width > 0) bubble.width else (dp(66f) * settings.bubbleScale).toInt()
        val toLeft = layout.x + width / 2 < screenWidth() / 2
        bubble.dockedLeft = toLeft
        val target = if (toLeft) margin else screenWidth() - width - margin
        animateX(target)
    }

    private fun animateX(target: Int) {
        val layout = params ?: return
        val from = layout.x
        if (from == target) {
            clampIntoScreen()
            return
        }
        val steps = 10
        for (step in 1..steps) {
            ui.postDelayed({
                val current = params ?: return@postDelayed
                val progress = step / steps.toFloat()
                val eased = 1f - (1f - progress) * (1f - progress)
                current.x = (from + (target - from) * eased).toInt()
                current.y = clampY(current.y)
                applyLayout()
                if (step == steps) savePosition()
            }, SNAP_STEP_MS * step)
        }
    }

    private fun clampIntoScreen() {
        val bubble = view ?: return
        val layout = params ?: return
        val width = if (bubble.width > 0) bubble.width else (dp(66f) * settings.bubbleScale).toInt()
        val margin = dp(EDGE_MARGIN_DP).toInt()
        layout.x = layout.x.coerceIn(margin - width / 3, screenWidth() - width + width / 3 - margin)
        layout.y = clampY(layout.y)
        applyLayout()
        savePosition()
    }

    private fun clampY(value: Int): Int {
        val bubble = view
        val height = if (bubble != null && bubble.height > 0) {
            bubble.height
        } else {
            (dp(66f) * settings.bubbleScale).toInt()
        }
        val top = dp(24f).toInt()
        val bottom = screenHeight() - height - dp(32f).toInt()
        if (bottom <= top) return top
        return value.coerceIn(top, bottom)
    }

    private fun applyLayout() {
        val current = view ?: return
        val layout = params ?: return
        runCatching { windowManager.updateViewLayout(current, layout) }
    }

    private fun savePosition() {
        val layout = params ?: return
        runCatching {
            prefs.edit().putInt(KEY_X, layout.x).putInt(KEY_Y, layout.y).apply()
        }
    }

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun dp(value: Float): Float = value * density

    private inline fun onUi(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else ui.post { block() }
    }
}

/**
 * Remembers whether the user has ever armed the bubble by starting a dub.
 *
 * The bubble must appear the first time "Start dubbing" is pressed and then stay
 * available for the next videos, so this flag survives the session, the service and
 * a reboot. Long pressing the bubble disarms it again.
 */
object BubbleArm {

    private const val PREFS = "doble_parsi_bubble"
    private const val KEY = "armed"

    fun arm(context: Context) = runCatching {
        prefs(context).edit().putBoolean(KEY, true).apply()
    }.let { }

    fun disarm(context: Context) = runCatching {
        prefs(context).edit().putBoolean(KEY, false).apply()
    }.let { }

    fun isArmed(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
