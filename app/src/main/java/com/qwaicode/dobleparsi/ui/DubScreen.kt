/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * @QW-AI-Code on GitHub
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qwaicode.dobleparsi.R
import com.qwaicode.dobleparsi.core.Languages
import com.qwaicode.dobleparsi.core.Loc
import com.qwaicode.dobleparsi.core.Settings
import com.qwaicode.dobleparsi.core.UsageSnapshot
import com.qwaicode.dobleparsi.service.DubUiState
import com.qwaicode.dobleparsi.service.Phase

/**
 * Doble Parsi 1.0.0 — main screen.
 *
 * Rebuilt from scratch for 1.0.0: navy dark, brand gradient pulled from the app
 * icon, card based layout, and an explicit colour on every single piece of text so
 * nothing can render dark-on-dark.
 */
@Composable
fun DubScreen(
    settings: Settings,
    state: DubUiState,
    usage: UsageSnapshot,
    needsNotificationAccess: Boolean,
    onSettingsChange: ((Settings) -> Settings) -> Unit,
    onToggleUiLang: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onClearUsage: () -> Unit
) {
    val running = state.phase != Phase.IDLE
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DP.ScreenGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(onToggleUiLang)

            StatusPanel(state, running, settings.uiLang)

            if (needsNotificationAccess) {
                NoticeBanner(
                    text = str(R.string.notif_access),
                    actionLabel = str(R.string.notif_access_open),
                    onAction = onOpenNotificationAccess
                )
            }

            state.errorCode?.let { code -> ErrorBanner(code, state.errorDetail) }

            Panel(title = str(R.string.section_key), icon = Icons.Filled.VpnKey) {
                ApiKeyField(settings, running, onSettingsChange)
            }

            Panel(title = str(R.string.section_engine), icon = Icons.Filled.Translate) {
                LanguagePicker(settings, running, onSettingsChange)
                Spacer(Modifier.height(10.dp))
                Chip(label = str(R.string.model), value = settings.translateModel)
            }

            Panel(title = str(R.string.section_outputs), icon = Icons.Filled.Bolt) {
                SwitchRow(
                    title = str(R.string.live_playback),
                    hint = str(R.string.live_playback_hint),
                    checked = settings.livePlayback
                ) { value -> onSettingsChange { it.copy(livePlayback = value) } }
                RowDivider()
                SwitchRow(
                    title = str(R.string.duck_original),
                    hint = str(R.string.duck_original_hint),
                    checked = settings.duckOriginal
                ) { value -> onSettingsChange { it.copy(duckOriginal = value) } }
                RowDivider()
                SwitchRow(
                    title = str(R.string.save_audio),
                    hint = str(R.string.save_audio_hint),
                    checked = settings.saveAudio
                ) { value -> onSettingsChange { it.copy(saveAudio = value) } }
                RowDivider()
                SwitchRow(
                    title = str(R.string.save_subtitle),
                    hint = str(R.string.save_subtitle_hint),
                    checked = settings.saveSubtitle
                ) { value -> onSettingsChange { it.copy(saveSubtitle = value) } }
                RowDivider()
                SwitchRow(
                    title = str(R.string.bilingual),
                    hint = str(R.string.bilingual_hint),
                    checked = settings.bilingualSubtitle
                ) { value -> onSettingsChange { it.copy(bilingualSubtitle = value) } }
            }

            BubblePanel(settings, onSettingsChange)

            AdvancedPanel(settings, onSettingsChange)

            PrimaryAction(
                label = if (running) str(R.string.stop) else str(R.string.start),
                running = running,
                enabled = state.phase != Phase.FINISHING,
                onClick = { if (running) onStop() else onStart() }
            )

            AnimatedVisibility(visible = state.dubText.isNotEmpty() || state.sourceText.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.dubText.isNotEmpty()) {
                        TranscriptPanel(str(R.string.dub_text), state.dubText, DP.Accent)
                    }
                    if (state.sourceText.isNotEmpty()) {
                        TranscriptPanel(str(R.string.source_text), state.sourceText, DP.TextSecondary)
                    }
                }
            }

            if (state.savedFiles.isNotEmpty() && !running) {
                Panel(title = str(R.string.saved_files), icon = Icons.Filled.Folder) {
                    state.savedFiles.forEach { name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(DP.Accent, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = DP.TextSecondary
                            )
                        }
                    }
                }
            }

            if (settings.tokenMonitor) {
                TokenPanel(
                    settings = settings,
                    usage = usage,
                    onSettingsChange = onSettingsChange,
                    onClearUsage = onClearUsage
                )
            }

            HowToPanel()
            AboutPanel()
            Footer()
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── header ──────────────────────────────────────────────────────────────────

@Composable
private fun Header(onToggleUiLang: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DP.BrandGradientDiagonal),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = str(R.string.app_name),
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = str(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DP.Text
            )
            Text(
                text = str(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = DP.TextSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        LanguageChip(onToggleUiLang)
    }
}

@Composable
private fun LanguageChip(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DP.CardHigh)
            .border(1.dp, DP.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = DP.Brand,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = str(R.string.lang_toggle),
            style = MaterialTheme.typography.labelLarge,
            color = DP.Text,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── status ──────────────────────────────────────────────────────────────────

@Composable
private fun StatusPanel(state: DubUiState, running: Boolean, lang: String) {
    val label = when {
        state.phase == Phase.CONNECTING -> str(R.string.status_connecting)
        state.phase == Phase.FINISHING -> str(R.string.status_finishing)
        state.speaking -> str(R.string.status_speaking)
        running -> str(R.string.status_live)
        else -> str(R.string.status_idle)
    }
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    val dot = when {
        state.speaking -> DP.Accent
        running -> DP.Brand
        else -> DP.TextMuted
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (running) {
                    Brush.horizontalGradient(
                        listOf(DP.BrandStart.copy(alpha = 0.22f), DP.BrandEnd.copy(alpha = 0.14f))
                    )
                } else {
                    Brush.horizontalGradient(listOf(DP.CardHigh, DP.Card))
                }
            )
            .border(1.dp, if (running) DP.Brand.copy(alpha = 0.45f) else DP.Border, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .background(
                        color = if (running) dot.copy(alpha = pulse) else dot.copy(alpha = 0.55f),
                        shape = CircleShape
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DP.Text
                )
                if (state.sessionName.isNotBlank()) {
                    Text(
                        text = state.sessionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = DP.TextSecondary,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatElapsed(state.elapsedMs, lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (running) DP.Accent else DP.TextMuted
            )
        }
    }
}

// ── panels ──────────────────────────────────────────────────────────────────

@Composable
internal fun Panel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(DP.Card)
            .border(1.dp, DP.Border, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DP.Brand.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = DP.Brand, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DP.Text,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = DP.BorderSoft)
}

@Composable
private fun Chip(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DP.Field)
            .border(1.dp, DP.BorderSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = DP.TextMuted
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = DP.TextSecondary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

@Composable
private fun ApiKeyField(
    settings: Settings,
    running: Boolean,
    onSettingsChange: ((Settings) -> Settings) -> Unit
) {
    OutlinedTextField(
        value = settings.apiKey,
        onValueChange = { value -> onSettingsChange { it.copy(apiKey = value.trim()) } },
        singleLine = true,
        enabled = !running,
        label = { Text(str(R.string.api_key)) },
        placeholder = { Text(str(R.string.api_key_hint)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = str(R.string.api_key_help),
        style = MaterialTheme.typography.bodySmall,
        color = DP.TextMuted
    )
}

/** Every text colour of the field is pinned; disabled state stays readable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DP.Text,
    unfocusedTextColor = DP.Text,
    disabledTextColor = DP.TextSecondary,
    errorTextColor = DP.Text,
    focusedContainerColor = DP.Field,
    unfocusedContainerColor = DP.Field,
    disabledContainerColor = DP.Field,
    errorContainerColor = DP.Field,
    cursorColor = DP.Brand,
    focusedBorderColor = DP.Brand,
    unfocusedBorderColor = DP.Border,
    disabledBorderColor = DP.BorderSoft,
    focusedLabelColor = DP.Brand,
    unfocusedLabelColor = DP.TextMuted,
    disabledLabelColor = DP.TextMuted,
    focusedPlaceholderColor = DP.TextMuted,
    unfocusedPlaceholderColor = DP.TextMuted,
    disabledPlaceholderColor = DP.TextMuted,
    focusedTrailingIconColor = DP.TextSecondary,
    unfocusedTrailingIconColor = DP.TextSecondary,
    disabledTrailingIconColor = DP.TextMuted
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(
    settings: Settings,
    running: Boolean,
    onSettingsChange: ((Settings) -> Settings) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = Languages.label(settings.targetLang, settings.uiLang)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!running) expanded = it }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = !running,
            label = { Text(str(R.string.target_lang)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DP.CardHigh)
        ) {
            Languages.ALL.forEach { language ->
                val selected = language.code == settings.targetLang
                DropdownMenuItem(
                    text = {
                        Text(
                            text = Languages.label(language.code, settings.uiLang),
                            color = if (selected) DP.Accent else DP.Text
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = DP.Text,
                        leadingIconColor = DP.Brand,
                        trailingIconColor = DP.Brand,
                        disabledTextColor = DP.TextMuted
                    ),
                    onClick = {
                        onSettingsChange { it.copy(targetLang = language.code) }
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 11.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = DP.Text
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = DP.TextMuted
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DP.Brand,
                checkedBorderColor = DP.Brand,
                checkedIconColor = DP.Brand,
                uncheckedThumbColor = DP.TextMuted,
                uncheckedTrackColor = DP.Field,
                uncheckedBorderColor = DP.Border,
                uncheckedIconColor = DP.Field
            )
        )
    }
}

@Composable
private fun AdvancedPanel(settings: Settings, onSettingsChange: ((Settings) -> Settings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Panel(
        title = str(R.string.section_advanced),
        icon = Icons.Filled.Tune,
        trailing = {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = DP.TextSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { expanded = !expanded }
                    .padding(4.dp)
            )
        }
    ) {
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SliderRow(
                    label = str(R.string.dub_volume),
                    value = "${(settings.dubVolume * 100).toInt()}%",
                    position = settings.dubVolume,
                    range = 0.2f..1f
                ) { value -> onSettingsChange { it.copy(dubVolume = value) } }

                SwitchRow(
                    title = str(R.string.low_latency),
                    hint = str(R.string.low_latency_hint),
                    checked = settings.lowLatency
                ) { value -> onSettingsChange { it.copy(lowLatency = value) } }

                SliderRow(
                    label = str(R.string.dub_sync),
                    value = "${settings.dubSyncMs} ${str(R.string.millis_short)}",
                    position = settings.dubSyncMs.toFloat(),
                    range = 0f..1500f
                ) { value -> onSettingsChange { it.copy(dubSyncMs = value.toInt()) } }

                SwitchRow(
                    title = str(R.string.strong_duck),
                    hint = str(R.string.strong_duck_hint),
                    checked = settings.strongDuck
                ) { value -> onSettingsChange { it.copy(strongDuck = value) } }

                SliderRow(
                    label = str(R.string.original_level),
                    value = "${(settings.originalLevel * 100).toInt()}%",
                    position = settings.originalLevel,
                    range = 0.02f..0.6f
                ) { value -> onSettingsChange { it.copy(originalLevel = value) } }

                SliderRow(
                    label = str(R.string.subtitle_offset),
                    value = "${settings.subtitleOffsetMs} ${str(R.string.millis_short)}",
                    position = settings.subtitleOffsetMs.toFloat(),
                    range = 0f..4000f
                ) { value -> onSettingsChange { it.copy(subtitleOffsetMs = value.toInt()) } }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = str(R.string.audio_source),
                    style = MaterialTheme.typography.labelMedium,
                    color = DP.TextMuted
                )
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf(str(R.string.audio_source_mix), str(R.string.audio_source_dub)),
                    selectedIndex = if (settings.audioSource == "mix") 0 else 1
                ) { index ->
                    onSettingsChange { it.copy(audioSource = if (index == 0) "mix" else "dub") }
                }

                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = str(R.string.monitor_tokens),
                    hint = str(R.string.monitor_tokens_hint),
                    checked = settings.tokenMonitor
                ) { value -> onSettingsChange { it.copy(tokenMonitor = value) } }
            }
        }
    }
}

@Composable
internal fun SliderRow(
    label: String,
    value: String,
    position: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = DP.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = DP.Accent
            )
        }
        Slider(
            value = position,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = DP.Brand,
                activeTrackColor = DP.Brand,
                inactiveTrackColor = DP.Field,
                activeTickColor = DP.Brand,
                inactiveTickColor = DP.Border
            )
        )
    }
}

@Composable
internal fun Segmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DP.Field)
            .border(1.dp, DP.BorderSoft, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) DP.Brand.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) DP.Text else DP.TextMuted,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PrimaryAction(label: String, running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val brush = if (running) DP.DangerGradient else DP.BrandGradient
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun TranscriptPanel(title: String, body: String, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DP.Card)
            .border(1.dp, DP.Border, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = DP.Text,
            modifier = Modifier.heightIn(max = 190.dp)
        )
    }
}

@Composable
private fun HowToPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DP.Brand.copy(alpha = 0.08f))
            .border(1.dp, DP.Brand.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = str(R.string.how_to_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = DP.Text
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = str(R.string.how_to),
            style = MaterialTheme.typography.bodySmall,
            color = DP.TextSecondary
        )
    }
}

@Composable
private fun Footer() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = str(R.string.app_version),
            style = MaterialTheme.typography.labelSmall,
            color = DP.TextMuted
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = com.qwaicode.dobleparsi.core.Credits.COPYRIGHT_LONG,
            style = MaterialTheme.typography.labelSmall,
            color = DP.TextMuted
        )
    }
}

@Composable
private fun NoticeBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DP.BrandStart.copy(alpha = 0.14f))
            .border(1.dp, DP.BrandStart.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = DP.Text,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = DP.BrandStart,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ErrorBanner(code: String, detail: String?) {
    val message = when (code) {
        "err.noKey" -> str(R.string.err_no_key)
        "err.noOutput" -> str(R.string.err_no_output)
        "err.capture" -> str(R.string.perm_capture)
        "err.socket" -> str(R.string.err_socket)
        "err.quota" -> str(R.string.err_quota)
        else -> detail ?: code
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DP.Danger.copy(alpha = 0.14f))
            .border(1.dp, DP.Danger.copy(alpha = 0.40f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = DP.DangerSoft
        )
    }
}

private fun formatElapsed(ms: Long, lang: String): String {
    val total = ms / 1000
    return String.format(Loc.locale(lang), "%02d:%02d", total / 60, total % 60)
}
