/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * @QW-AI-Code on GitHub
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.qwaicode.dobleparsi.R
import com.qwaicode.dobleparsi.core.Loc
import com.qwaicode.dobleparsi.core.Settings
import com.qwaicode.dobleparsi.core.UsageSnapshot
import com.qwaicode.dobleparsi.core.UsageStore
import java.text.NumberFormat

/**
 * Gemini API token monitor.
 *
 * Counts come from the `usageMetadata` block the Live API attaches to its server
 * messages. Google does not publish an endpoint for "remaining daily quota", so the
 * remaining figure is measured against the daily budget the user copies from AI
 * Studio, and a real 429 / RESOURCE_EXHAUSTED answer is surfaced as the hard limit.
 * Daily counters roll over at midnight Pacific time, like the API's own RPD/TPD.
 */
@Composable
fun TokenPanel(
    settings: Settings,
    usage: UsageSnapshot,
    onSettingsChange: ((Settings) -> Settings) -> Unit,
    onClearUsage: () -> Unit
) {
    val context = LocalContext.current
    val lang = settings.uiLang
    val today = usage.today
    val limit = settings.dailyTokenLimit
    val remaining = if (limit > 0) maxOf(0L, limit - today.total) else null
    val fraction = if (limit > 0) (today.total.toFloat() / limit).coerceIn(0f, 1f) else null

    Panel(title = str(R.string.section_tokens), icon = Icons.Filled.DataUsage) {

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = str(R.string.tokens_today),
                    style = MaterialTheme.typography.labelMedium,
                    color = DP.TextMuted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = number(today.total, lang),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = DP.Text
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (remaining != null) {
                        str(R.string.tokens_remaining)
                    } else {
                        str(R.string.tokens_limit_unset)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = DP.TextMuted
                )
                if (remaining != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = number(remaining, lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (fraction != null && fraction > 0.9f) DP.DangerSoft else DP.Accent
                    )
                }
            }
        }

        if (fraction != null) {
            Spacer(Modifier.height(10.dp))
            UsageBar(fraction)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = DP.TextMuted
            )
        }

        Spacer(Modifier.height(12.dp))
        RowDivider()

        StatRow(str(R.string.tokens_input), number(today.input, lang))
        StatRow(str(R.string.tokens_output), number(today.output, lang))
        StatRow(str(R.string.tokens_audio), "${number(today.audioIn, lang)} / ${number(today.audioOut, lang)}")
        StatRow(str(R.string.tokens_turns), number(today.updates.toLong(), lang))
        StatRow(str(R.string.tokens_sessions), number(today.sessions.toLong(), lang))
        StatRow(str(R.string.tokens_peak), number(today.peakContext.toLong(), lang))
        StatRow(str(R.string.tokens_reset_in), countdown(usage.resetAtMs, lang))

        if (usage.quota.hit) {
            Spacer(Modifier.height(12.dp))
            QuotaBanner(usage, lang)
        }

        Spacer(Modifier.height(14.dp))

        var limitText by remember(limit) {
            mutableStateOf(if (limit > 0) limit.toString() else "")
        }
        OutlinedTextField(
            value = limitText,
            onValueChange = { value ->
                val digits = value.filter { it.isDigit() }.take(9)
                limitText = digits
                onSettingsChange { it.copy(dailyTokenLimit = digits.toIntOrNull() ?: 0) }
            },
            singleLine = true,
            label = { Text(str(R.string.tokens_limit)) },
            placeholder = { Text(str(R.string.tokens_limit_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = str(R.string.tokens_note),
            style = MaterialTheme.typography.labelSmall,
            color = DP.TextMuted
        )

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(DP.Brand.copy(alpha = 0.14f))
                    .border(1.dp, DP.Brand.copy(alpha = 0.28f), RoundedCornerShape(11.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(UsageStore.DASHBOARD_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = null,
                    tint = DP.Brand,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = str(R.string.tokens_dashboard),
                    style = MaterialTheme.typography.labelMedium,
                    color = DP.Text
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = str(R.string.tokens_reset_counter),
                style = MaterialTheme.typography.labelMedium,
                color = DP.TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .clickable(onClick = onClearUsage)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        val history = usage.history.filter { it.day != today.day }.take(4)
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RowDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = str(R.string.tokens_history),
                style = MaterialTheme.typography.labelMedium,
                color = DP.TextMuted
            )
            Spacer(Modifier.height(4.dp))
            history.forEach { day ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        text = day.day,
                        style = MaterialTheme.typography.labelSmall,
                        color = DP.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = number(day.total, lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = DP.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(50))
            .background(DP.Field)
            .border(1.dp, DP.BorderSoft, RoundedCornerShape(50))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                .background(if (fraction > 0.9f) DP.DangerGradient else DP.BrandGradient)
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = DP.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = DP.Text,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuotaBanner(usage: UsageSnapshot, lang: String) {
    val retry = usage.quota.retryAtMs
    val message = buildString {
        append(str(R.string.tokens_quota_hit))
        if (retry > System.currentTimeMillis()) {
            append("\n")
            append(str(R.string.tokens_retry_in))
            append(" ")
            append(countdown(retry, lang))
        }
        usage.quota.message?.takeIf { it.isNotBlank() }?.let {
            append("\n")
            append(it)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DP.Danger.copy(alpha = 0.14f))
            .border(1.dp, DP.Danger.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = DP.DangerSoft
        )
    }
}

private fun number(value: Long, lang: String): String = runCatching {
    NumberFormat.getIntegerInstance(Loc.locale(lang)).format(value)
}.getOrElse { value.toString() }

/** "h:mm" left until the given instant, in the UI language's digits. */
private fun countdown(atMs: Long, lang: String): String {
    val left = atMs - System.currentTimeMillis()
    if (atMs <= 0L || left <= 0L) return "—"
    val minutes = left / 60_000L
    return runCatching {
        String.format(Loc.locale(lang), "%d:%02d", minutes / 60, minutes % 60)
    }.getOrElse { "${minutes / 60}:${minutes % 60}" }
}
