/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Source and releases: github.com/QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.core

/**
 * Single source of truth for authorship and copyright.
 *
 * The same strings are stamped on every source file, shown in the about card and
 * written into the release notes, so the credit can never drift.
 */
object Credits {

    const val AUTHOR = "QW-AI-Code"
    const val GITHUB_HANDLE = "@QW-AI-Code"
    const val GITHUB_URL = "https://github.com/QW-AI-Code"
    const val GITHUB_SHORT = "github.com/QW-AI-Code"
    const val YEAR = "2026"
    const val VERSION = "1.0.0"

    const val COPYRIGHT = "© $YEAR $AUTHOR"
    const val COPYRIGHT_LONG = "© $YEAR $AUTHOR - $GITHUB_SHORT"

    /**
     * Rotating signature lines. One is picked at random per app launch, and the
     * same pool is stamped across the source files, so the author's GitHub name
     * shows up randomly all over the project instead of in a single hard-coded
     * footer.
     */
    val SIGNATURES: List<String> = listOf(
        "Crafted by $AUTHOR",
        "Built with care by $AUTHOR",
        "Engineered by $AUTHOR",
        "Made in the open by $AUTHOR",
        "Source and releases: $GITHUB_SHORT",
        "$GITHUB_HANDLE on GitHub",
        "An $AUTHOR project"
    )

    /** Random signature for this launch. */
    fun signature(): String = SIGNATURES.random()

    /** Deterministic signature, for places that must not flicker on recomposition. */
    fun signature(seed: Int): String = SIGNATURES[Math.floorMod(seed, SIGNATURES.size)]
}
