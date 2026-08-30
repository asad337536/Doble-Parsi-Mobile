/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Built with care by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * The floating control, drawn by hand.
 *
 * Everything the user asked for lives here: a professional glass + gradient look, a
 * live "someone is talking" animation driven by the real dub level while the model
 * speaks, and the running dub time inside the bubble. Idle it collapses into a small
 * edge tab and fades down so it never gets in the way of the video.
 */
class BubbleView(context: Context) : View(context) {

    enum class Mode { IDLE, CONNECTING, LIVE, SPEAKING, FINISHING }

    private companion object {
        const val BRAND_START = 0xFF7C5CFF.toInt()
        const val BRAND_END = 0xFF2E7BFF.toInt()
        const val ACCENT = 0xFF2DD4BF.toInt()
        const val DANGER = 0xFFFF5F6D.toInt()
        const val GLASS = 0xF20B1220.toInt()
        const val TEXT = 0xFFF2F6FF.toInt()
        const val MUTED = 0xFF93A2C0.toInt()
        const val BASE_DP = 66f
        const val TAB_W_DP = 20f
        const val TAB_H_DP = 46f
        const val BARS = 5
    }

    private val density = context.resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = TEXT
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = TEXT
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = TEXT
    }

    private val bounds = RectF()
    private val ringMatrix = Matrix()
    private var ringShader: SweepGradient? = null
    private val playPath = Path()

    private var frameStart = System.currentTimeMillis()
    private var animating = false
    private var smoothedLevel = 0f
    private val barLevels = FloatArray(BARS)

    var mode: Mode = Mode.IDLE
        set(value) {
            if (field == value) return
            field = value
            if (value != Mode.SPEAKING) smoothedLevel = 0f
            syncAnimation()
            invalidate()
        }

    /** 0..1 loudness of the dub that is actually being played. */
    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    var elapsedMs: Long = 0
        set(value) {
            val changedSecond = value / 1000 != field / 1000
            field = value
            if (changedSecond) invalidate()
        }

    var showTimer: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var collapsed: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    /** Which side the collapsed tab is docked to. */
    var dockedLeft: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var scaleFactor: Float = 1f
        set(value) {
            val next = value.coerceIn(0.7f, 1.6f)
            if (field == next) return
            field = next
            requestLayout()
            invalidate()
        }

    var locale: Locale = Locale.getDefault()

    private val frame = object : Runnable {
        override fun run() {
            if (!animating) return
            invalidate()
            postDelayed(this, 33L)
        }
    }

    init {
        isClickable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun dp(value: Float): Float = value * density

    private fun diameter(): Int = (dp(BASE_DP) * scaleFactor).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (collapsed) {
            setMeasuredDimension(
                (dp(TAB_W_DP) * scaleFactor).toInt(),
                (dp(TAB_H_DP) * scaleFactor).toInt()
            )
        } else {
            val size = diameter()
            setMeasuredDimension(size, size)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildRingShader()
    }

    private fun buildRingShader() {
        if (width <= 0 || height <= 0) return
        ringShader = SweepGradient(
            width / 2f, height / 2f,
            intArrayOf(BRAND_START, BRAND_END, ACCENT, BRAND_START),
            floatArrayOf(0f, 0.42f, 0.72f, 1f)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimation()
    }

    override fun onDetachedFromWindow() {
        animating = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private fun syncAnimation() {
        val wanted = isAttachedToWindow && !collapsed && mode != Mode.IDLE
        if (wanted == animating) return
        animating = wanted
        removeCallbacks(frame)
        if (wanted) post(frame)
    }

    override fun onDraw(canvas: Canvas) {
        if (collapsed) {
            drawTab(canvas)
            return
        }
        val t = (System.currentTimeMillis() - frameStart) / 1000f
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val pad = dp(4f)
        val radius = size / 2f - pad

        // Talking halo: grows with the real dub level.
        smoothedLevel += ((if (mode == Mode.SPEAKING) level else 0f) - smoothedLevel) * 0.35f
        if (smoothedLevel > 0.02f) {
            fill.color = ACCENT
            fill.alpha = (60 * smoothedLevel).toInt().coerceIn(0, 90)
            canvas.drawCircle(cx, cy, radius + dp(3f) + dp(5f) * smoothedLevel, fill)
        }

        // Glass body with a soft drop shadow.
        fill.color = GLASS
        fill.alpha = 242
        fill.setShadowLayer(dp(7f), 0f, dp(2f), 0x99000000.toInt())
        canvas.drawCircle(cx, cy, radius, fill)
        fill.clearShadowLayer()

        // Gradient ring, slowly rotating while a session is running.
        if (ringShader == null) buildRingShader()
        val spin = when (mode) {
            Mode.IDLE -> 0f
            Mode.FINISHING -> t * 220f
            else -> t * 55f
        }
        ringMatrix.setRotate(spin, cx, cy)
        ringShader?.setLocalMatrix(ringMatrix)
        ring.shader = ringShader
        ring.strokeWidth = dp(2.6f)
        ring.alpha = if (mode == Mode.IDLE) 205 else 255
        canvas.drawCircle(cx, cy, radius - dp(1.3f), ring)
        ring.shader = null

        // Seconds arc: one full turn per minute, so the bubble always looks alive.
        if (mode != Mode.IDLE) {
            bounds.set(
                cx - radius + dp(5.5f), cy - radius + dp(5.5f),
                cx + radius - dp(5.5f), cy + radius - dp(5.5f)
            )
            arc.strokeWidth = dp(1.8f)
            arc.color = if (mode == Mode.SPEAKING) ACCENT else BRAND_END
            arc.alpha = 150
            val sweep = ((elapsedMs % 60_000L) / 60_000f) * 360f
            canvas.drawArc(bounds, -90f, sweep, false, arc)
        }

        val contentCy = if (showTimer && mode != Mode.IDLE) cy - dp(5f) else cy

        when (mode) {
            Mode.IDLE -> drawPlayMark(canvas, cx, cy, radius)
            Mode.CONNECTING -> drawDots(canvas, cx, contentCy, t)
            Mode.FINISHING -> drawSpinner(canvas, cx, contentCy, radius, t)
            Mode.LIVE, Mode.SPEAKING -> drawBars(canvas, cx, contentCy, t)
        }

        if (showTimer && mode != Mode.IDLE) {
            label.textSize = dp(10.5f) * scaleFactor
            label.color = if (mode == Mode.SPEAKING) ACCENT else MUTED
            canvas.drawText(timeText(), cx, cy + radius * 0.60f, label)
        }
    }

    /** Idle: the app mark (play + two waves), so a tap clearly means "start". */
    private fun drawPlayMark(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val s = radius * 0.52f
        playPath.reset()
        playPath.moveTo(cx - s * 0.62f, cy - s * 0.72f)
        playPath.lineTo(cx - s * 0.62f, cy + s * 0.72f)
        playPath.lineTo(cx + s * 0.42f, cy)
        playPath.close()
        glyph.color = TEXT
        glyph.alpha = 255
        canvas.drawPath(playPath, glyph)

        stroke.strokeWidth = dp(1.7f)
        stroke.color = ACCENT
        stroke.alpha = 220
        bounds.set(cx + s * 0.2f, cy - s * 0.62f, cx + s * 1.5f, cy + s * 0.62f)
        canvas.drawArc(bounds, -52f, 104f, false, stroke)
        bounds.set(cx + s * 0.05f, cy - s * 1.02f, cx + s * 2.05f, cy + s * 1.02f)
        stroke.alpha = 130
        canvas.drawArc(bounds, -46f, 92f, false, stroke)
    }

    /** Live: five bars that move with the dub, exactly like a person talking. */
    private fun drawBars(canvas: Canvas, cx: Float, cy: Float, t: Float) {
        val barWidth = dp(2.8f) * scaleFactor
        val gap = dp(2.4f) * scaleFactor
        val maxHeight = dp(17f) * scaleFactor
        val minHeight = dp(3.2f) * scaleFactor
        val totalWidth = BARS * barWidth + (BARS - 1) * gap
        var x = cx - totalWidth / 2f
        val speaking = mode == Mode.SPEAKING
        val drive = if (speaking) 0.35f + smoothedLevel * 0.9f else 0.14f

        for (i in 0 until BARS) {
            val phase = t * (if (speaking) 7.4f else 2.1f) + i * 0.95f
            val shape = if (i == 2) 1f else if (i == 1 || i == 3) 0.82f else 0.62f
            val wave = (0.55f + 0.45f * sin(phase.toDouble()).toFloat()) * shape
            val target = (minHeight + (maxHeight - minHeight) * wave * drive)
                .coerceIn(minHeight, maxHeight)
            barLevels[i] += (target - barLevels[i]) * 0.45f
            val half = barLevels[i] / 2f
            glyph.color = if (speaking) ACCENT else TEXT
            glyph.alpha = if (speaking) 255 else 190
            canvas.drawRoundRect(
                x, cy - half, x + barWidth, cy + half,
                barWidth / 2f, barWidth / 2f, glyph
            )
            x += barWidth + gap
        }
    }

    private fun drawDots(canvas: Canvas, cx: Float, cy: Float, t: Float) {
        val r = dp(2.7f) * scaleFactor
        val gap = dp(7f) * scaleFactor
        for (i in -1..1) {
            val pulse = 0.4f + 0.6f * abs(sin((t * 3.1f + i * 0.6f).toDouble()).toFloat())
            glyph.color = BRAND_END
            glyph.alpha = (255 * pulse).toInt().coerceIn(60, 255)
            canvas.drawCircle(cx + i * gap, cy, r * (0.75f + 0.35f * pulse), glyph)
        }
    }

    private fun drawSpinner(canvas: Canvas, cx: Float, cy: Float, radius: Float, t: Float) {
        val r = radius * 0.42f
        bounds.set(cx - r, cy - r, cx + r, cy + r)
        arc.strokeWidth = dp(2.4f)
        arc.color = BRAND_END
        arc.alpha = 255
        canvas.drawArc(bounds, (t * 260f) % 360f, 96f, false, arc)
        arc.color = ACCENT
        arc.alpha = 160
        canvas.drawArc(bounds, (t * 260f + 180f) % 360f, 60f, false, arc)
    }

    /** Collapsed idle state: a slim tab hugging the screen edge. */
    private fun drawTab(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = dp(11f)
        fill.color = GLASS
        fill.alpha = 235
        fill.setShadowLayer(dp(5f), 0f, dp(1.5f), 0x88000000.toInt())
        bounds.set(if (dockedLeft) -r else 0f, 0f, if (dockedLeft) w else w + r, h)
        canvas.drawRoundRect(bounds, r, r, fill)
        fill.clearShadowLayer()

        glyph.color = if (mode == Mode.IDLE) BRAND_END else ACCENT
        glyph.alpha = 255
        val cx = w / 2f + if (dockedLeft) dp(1f) else -dp(1f)
        val cy = h / 2f
        val barW = dp(2.2f)
        val heights = floatArrayOf(dp(7f), dp(12f), dp(7f))
        var x = cx - (3 * barW + 2 * dp(2f)) / 2f
        for (index in 0..2) {
            val half = heights[index] / 2f
            canvas.drawRoundRect(x, cy - half, x + barW, cy + half, barW, barW, glyph)
            x += barW + dp(2f)
        }
    }

    private fun timeText(): String {
        val total = elapsedMs / 1000
        return runCatching {
            String.format(locale, "%02d:%02d", total / 60, total % 60)
        }.getOrElse { "%02d:%02d".format(total / 60, total % 60) }
    }

    /** Repaint request from the outside (level meter, timer tick). */
    fun refresh() {
        if (!animating) invalidate()
    }
}
