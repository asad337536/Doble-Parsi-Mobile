/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * An QW-AI-Code project
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.qwaicode.dobleparsi.core.Loc

/**
 * UI language is resolved by the app itself instead of trusting the configuration
 * that reaches the Activity.
 *
 * Why: the Activity used to declare `locale|layoutDirection` in configChanges, so
 * Android never re-created it after a per-app locale switch. Half of the strings
 * kept coming from the cached Persian resources and the layout stayed RTL. Now
 * every string is read from a Resources object built for the selected language and
 * the layout direction is forced to match, so switching to English flips the whole
 * screen to LTR and every single label is English.
 */
@Immutable
class Strings(private val context: Context, val lang: String) {

    val isRtl: Boolean = Loc.isRtl(lang)

    operator fun get(id: Int): String = Loc.string(context, lang, id)

    fun format(id: Int, vararg args: Any): String = Loc.string(context, lang, id, *args)
}

val LocalStrings = staticCompositionLocalOf<Strings> {
    error("LocalStrings was used outside of AppLocale")
}

@Composable
fun AppLocale(lang: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val strings = remember(lang, context, configuration) { Strings(context, lang) }
    val direction = if (strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLayoutDirection provides direction,
        content = content
    )
}

/** Locale-correct replacement for stringResource(). */
@Composable
fun str(id: Int): String = LocalStrings.current[id]
