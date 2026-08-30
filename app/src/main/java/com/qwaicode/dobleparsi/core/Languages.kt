/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Source and releases: github.com/QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.core

/** Dub target languages — ported from the Chrome extension (src/languages.js). */
data class DubLanguage(val code: String, val fa: String, val en: String)

object Languages {

    val ALL: List<DubLanguage> = listOf(
        DubLanguage("fa", "فارسی", "Persian"),
        DubLanguage("en", "انگلیسی", "English"),
        DubLanguage("ar", "عربی", "Arabic"),
        DubLanguage("tr", "ترکی استانبولی", "Turkish"),
        DubLanguage("az", "ترکی آذربایجانی", "Azerbaijani"),
        DubLanguage("de", "آلمانی", "German"),
        DubLanguage("fr", "فرانسوی", "French"),
        DubLanguage("es", "اسپانیایی", "Spanish"),
        DubLanguage("it", "ایتالیایی", "Italian"),
        DubLanguage("ru", "روسی", "Russian"),
        DubLanguage("hi", "هندی", "Hindi"),
        DubLanguage("ur", "اردو", "Urdu"),
        DubLanguage("zh-Hans", "چینی (ساده)", "Chinese (Simplified)"),
        DubLanguage("ja", "ژاپنی", "Japanese"),
        DubLanguage("ko", "کره‌ای", "Korean"),
        DubLanguage("pt-BR", "پرتغالی (برزیل)", "Portuguese (Brazil)"),
        DubLanguage("nl", "هلندی", "Dutch"),
        DubLanguage("sv", "سوئدی", "Swedish"),
        DubLanguage("pl", "لهستانی", "Polish"),
        DubLanguage("id", "اندونزیایی", "Indonesian"),
        DubLanguage("vi", "ویتنامی", "Vietnamese"),
        DubLanguage("he", "عبری", "Hebrew"),
        DubLanguage("el", "یونانی", "Greek"),
        DubLanguage("th", "تایلندی", "Thai"),
        DubLanguage("uk", "اوکراینی", "Ukrainian"),
        DubLanguage("ro", "رومانیایی", "Romanian"),
        DubLanguage("hu", "مجاری", "Hungarian"),
        DubLanguage("cs", "چکی", "Czech"),
        DubLanguage("bn", "بنگالی", "Bengali"),
        DubLanguage("ta", "تامیلی", "Tamil"),
        DubLanguage("sw", "سواحیلی", "Swahili")
    )

    /** Live translation models that replicate the original speaker's voice. */
    val MODELS: List<String> = listOf("gemini-3.5-live-translate-preview")

    fun label(code: String, uiLang: String): String {
        val item = ALL.firstOrNull { it.code == code } ?: return code
        return if (uiLang == "en") item.en else item.fa
    }
}
