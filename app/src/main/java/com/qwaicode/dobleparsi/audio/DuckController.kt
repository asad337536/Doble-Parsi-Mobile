/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Engineered by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Ducks whatever else is playing while the dub voice speaks.
 *
 * On Android an app cannot touch another app's volume, so instead of a gain node we
 * take transient audio focus with MAY_DUCK: well behaved players (YouTube, Telegram,
 * browsers) lower themselves and come back up when we release it.
 */
class DuckController(context: Context, private val releaseMs: Long = 420L) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null
    private var held = false

    private val release = Runnable { abandon() }

    fun duck() {
        handler.removeCallbacks(release)
        if (held) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener({ }, handler)
            .build()
        request = focus
        held = audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun scheduleRelease() {
        handler.removeCallbacks(release)
        handler.postDelayed(release, releaseMs)
    }

    fun abandon() {
        handler.removeCallbacks(release)
        request?.let { audioManager.abandonAudioFocusRequest(it) }
        request = null
        held = false
    }
}
