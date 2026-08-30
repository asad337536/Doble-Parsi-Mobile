/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Made in the open by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Language helper shared by the UI and the foreground service.
 *
 * The service must not rely on the per-app locale either: on Android 12 and below
 * the AppCompat locale backport only re-configures activities, which is exactly why
 * the notification kept showing Persian text after switching the app to English.
 */
object Loc {

    private val cache = HashMap<String, Resources>()

    private val RTL = setOf("fa", "ar", "he", "ur")

    fun isRtl(lang: String): Boolean = RTL.contains(lang.lowercase(Locale.ROOT).take(2))

    fun locale(lang: String): Locale = Locale.forLanguageTag(lang)

    @Synchronized
    fun resources(context: Context, lang: String): Resources {
        cache[lang]?.let { return it }
        val locale = locale(lang)
        val configuration = Configuration(context.applicationContext.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        val resources = context.applicationContext
            .createConfigurationContext(configuration)
            .resources
        cache[lang] = resources
        return resources
    }

    fun string(context: Context, lang: String, id: Int): String =
        runCatching { resources(context, lang).getString(id) }
            .getOrElse { context.getString(id) }

    fun string(context: Context, lang: String, id: Int, vararg args: Any): String =
        runCatching { resources(context, lang).getString(id, *args) }
            .getOrElse { context.getString(id, *args) }
}
