/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Engineered by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qwaicode.dobleparsi.R

/**
 * Doble Parsi 1.0.0 design tokens.
 *
 * Navy dark, single source of truth. Every colour role of the Material scheme is
 * pinned on purpose: nothing is left to the platform, so no widget can ever fall
 * back to a light-mode (black) text colour on top of the dark surfaces.
 */
object DP {
    val Bg = Color(0xFF060A14)
    val BgSoft = Color(0xFF0A1120)
    val Card = Color(0xFF0F1626)
    val CardHigh = Color(0xFF131C30)
    val Field = Color(0xFF0C1322)
    val Border = Color(0xFF1E2942)
    val BorderSoft = Color(0xFF17203A)

    val Text = Color(0xFFF2F6FF)
    val TextSecondary = Color(0xFFA7B4CE)
    val TextMuted = Color(0xFF7C8AA8)

    val BrandStart = Color(0xFF7C5CFF)
    val BrandEnd = Color(0xFF2E7BFF)
    val Brand = Color(0xFF4C82FF)
    val Accent = Color(0xFF2DD4BF)
    val Danger = Color(0xFFFF5F6D)
    val DangerSoft = Color(0xFFFFD9DC)

    val BrandGradient = Brush.horizontalGradient(listOf(BrandStart, BrandEnd))
    val BrandGradientDiagonal = Brush.linearGradient(listOf(BrandStart, BrandEnd))
    val DangerGradient = Brush.horizontalGradient(listOf(Color(0xFFFF7A5F), Danger))
    val ScreenGradient = Brush.verticalGradient(listOf(BgSoft, Bg, Bg))
}

private val Vazirmatn = FontFamily(Font(R.font.vazirmatn, FontWeight.Normal))

private val DarkScheme = darkColorScheme(
    primary = DP.Brand,
    onPrimary = Color.White,
    primaryContainer = DP.BrandStart,
    onPrimaryContainer = Color.White,
    inversePrimary = DP.BrandStart,
    secondary = DP.Accent,
    onSecondary = Color(0xFF04241C),
    secondaryContainer = DP.CardHigh,
    onSecondaryContainer = DP.Text,
    tertiary = DP.BrandStart,
    onTertiary = Color.White,
    tertiaryContainer = DP.CardHigh,
    onTertiaryContainer = DP.Text,
    background = DP.Bg,
    onBackground = DP.Text,
    surface = DP.Card,
    onSurface = DP.Text,
    surfaceVariant = DP.CardHigh,
    onSurfaceVariant = DP.TextSecondary,
    surfaceTint = DP.Brand,
    surfaceBright = DP.CardHigh,
    surfaceDim = DP.Bg,
    surfaceContainer = DP.CardHigh,
    surfaceContainerHigh = Color(0xFF17213A),
    surfaceContainerHighest = Color(0xFF1B2743),
    surfaceContainerLow = DP.Card,
    surfaceContainerLowest = DP.Bg,
    inverseSurface = DP.Text,
    inverseOnSurface = DP.Bg,
    error = DP.Danger,
    onError = Color.White,
    errorContainer = Color(0xFF4A1520),
    onErrorContainer = DP.DangerSoft,
    outline = DP.Border,
    outlineVariant = DP.BorderSoft,
    scrim = Color(0xCC000000)
)

private val DPTypography: Typography
    get() {
        val base = Typography()
        fun apply(style: androidx.compose.ui.text.TextStyle) = style.copy(fontFamily = Vazirmatn)
        return Typography(
            displayLarge = apply(base.displayLarge),
            displayMedium = apply(base.displayMedium),
            displaySmall = apply(base.displaySmall),
            headlineLarge = apply(base.headlineLarge),
            headlineMedium = apply(base.headlineMedium),
            headlineSmall = apply(base.headlineSmall),
            titleLarge = apply(base.titleLarge).copy(fontSize = 21.sp),
            titleMedium = apply(base.titleMedium),
            titleSmall = apply(base.titleSmall),
            bodyLarge = apply(base.bodyLarge),
            bodyMedium = apply(base.bodyMedium),
            bodySmall = apply(base.bodySmall),
            labelLarge = apply(base.labelLarge),
            labelMedium = apply(base.labelMedium),
            labelSmall = apply(base.labelSmall)
        )
    }

@Composable
fun DobleParsiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = DPTypography) {
        // Default content colour is light, so an un-styled Text can never be black.
        CompositionLocalProvider(LocalContentColor provides DP.Text, content = content)
    }
}
