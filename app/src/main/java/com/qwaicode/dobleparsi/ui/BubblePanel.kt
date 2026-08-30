/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * Made in the open by QW-AI-Code
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qwaicode.dobleparsi.R
import com.qwaicode.dobleparsi.core.Settings

/**
 * Everything about the floating control.
 *
 * The bubble is a full session control: it appears the first time a dub is started,
 * survives the end of the session so the next video can be started straight from it,
 * dims and collapses while idle, snaps to the closest edge and shows the running dub
 * time. Long pressing it removes it until the next "Start dubbing".
 */
@Composable
fun BubblePanel(settings: Settings, onSettingsChange: ((Settings) -> Settings) -> Unit) {
    Panel(title = str(R.string.section_bubble), icon = Icons.Filled.Adjust) {
        SwitchRow(
            title = str(R.string.bubble),
            hint = str(R.string.bubble_hint),
            checked = settings.showBubble
        ) { value -> onSettingsChange { it.copy(showBubble = value) } }

        if (settings.showBubble) {
            RowDivider()
            SwitchRow(
                title = str(R.string.bubble_keep),
                hint = str(R.string.bubble_keep_hint),
                checked = settings.keepBubble
            ) { value -> onSettingsChange { it.copy(keepBubble = value) } }
            RowDivider()
            SwitchRow(
                title = str(R.string.bubble_timer),
                hint = str(R.string.bubble_timer_hint),
                checked = settings.bubbleTimer
            ) { value -> onSettingsChange { it.copy(bubbleTimer = value) } }
            RowDivider()
            SwitchRow(
                title = str(R.string.bubble_fade),
                hint = str(R.string.bubble_fade_hint),
                checked = settings.bubbleFade
            ) { value -> onSettingsChange { it.copy(bubbleFade = value) } }
            RowDivider()
            SwitchRow(
                title = str(R.string.bubble_collapse),
                hint = str(R.string.bubble_collapse_hint),
                checked = settings.bubbleCollapse
            ) { value -> onSettingsChange { it.copy(bubbleCollapse = value) } }
            RowDivider()
            SwitchRow(
                title = str(R.string.bubble_snap),
                hint = str(R.string.bubble_snap_hint),
                checked = settings.bubbleSnap
            ) { value -> onSettingsChange { it.copy(bubbleSnap = value) } }
            RowDivider()
            SwitchRow(
                title = str(R.string.bubble_smart),
                hint = str(R.string.bubble_smart_hint),
                checked = settings.bubbleSmart
            ) { value -> onSettingsChange { it.copy(bubbleSmart = value) } }
            RowDivider()
            Spacer(Modifier.height(8.dp))
            SliderRow(
                label = str(R.string.bubble_scale),
                value = "${(settings.bubbleScale * 100).toInt()}%",
                position = settings.bubbleScale,
                range = 0.8f..1.4f
            ) { value -> onSettingsChange { it.copy(bubbleScale = value) } }
            Text(
                text = str(R.string.bubble_tip),
                style = MaterialTheme.typography.labelSmall,
                color = DP.TextMuted
            )
        }
    }
}
