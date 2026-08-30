/*
 * Doble Parsi V1.0.0 - live AI dubbing for Android
 * @QW-AI-Code on GitHub
 *
 * Copyright (c) 2026 QW-AI-Code - https://github.com/QW-AI-Code
 * SPDX-FileCopyrightText: 2026 QW-AI-Code (github.com/QW-AI-Code)
 */
package com.qwaicode.dobleparsi.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Phase { IDLE, CONNECTING, LIVE, FINISHING }

data class DubUiState(
    val phase: Phase = Phase.IDLE,
    val speaking: Boolean = false,
    val elapsedMs: Long = 0,
    val sourceText: String = "",
    val dubText: String = "",
    val sessionName: String = "",
    val errorCode: String? = null,
    val errorDetail: String? = null,
    val savedFiles: List<String> = emptyList()
)

/** Single source of truth shared by the service, the UI and the floating bubble. */
object SessionState {

    private val _state = MutableStateFlow(DubUiState())
    val state: StateFlow<DubUiState> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value.phase != Phase.IDLE

    fun update(transform: (DubUiState) -> DubUiState) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = DubUiState()
    }
}
