/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Engineered by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.qwaicode.dobleparsi.core.SettingsStore

/**
 * Doble Parsi for Android — live AI dubbing of any audio playing on the phone.
 * Built by QW-AI-Code — https://github.com/QW-AI-Code
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val tag = SettingsStore.get(this).value.uiLang
            val current = AppCompatDelegate.getApplicationLocales()
            if (current.isEmpty || current.toLanguageTags() != tag) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        }
    }
}
