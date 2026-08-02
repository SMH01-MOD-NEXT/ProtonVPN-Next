/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.ConnectionVerificationMode
import ru.protonmod.next.data.local.SettingsManager

data class ConnectionVerificationUiState(
    val mode: ConnectionVerificationMode = ConnectionVerificationMode.BALANCED,
    val requireVerification: Boolean = false,
    val requirePreflight: Boolean = false,
    val detectFailures: Boolean = true,
    val autoReconnect: Boolean = true,
    val handshakeTimeoutSeconds: Int = SettingsManager.DEFAULT_HANDSHAKE_RECONNECT_TIMEOUT_SECONDS,
)

@HiltViewModel
class ConnectionVerificationSettingsViewModel @Inject constructor(
    private val settings: SettingsManager,
) : ViewModel() {
    private val verification = combine(
        settings.connectionVerificationMode,
        settings.connectionVerificationRequired,
        settings.connectionPreflightRequired,
    ) { mode, required, preflight -> Triple(mode, required, preflight) }

    private val recovery = combine(
        settings.connectionFailureDetection,
        settings.connectionAutoReconnect,
        settings.handshakeReconnectTimeoutSeconds,
    ) { detection, reconnect, timeout -> Triple(detection, reconnect, timeout) }

    val uiState: StateFlow<ConnectionVerificationUiState> = combine(
        verification,
        recovery,
    ) { verificationState, recoveryState ->
        ConnectionVerificationUiState(
            mode = verificationState.first,
            requireVerification = verificationState.second,
            requirePreflight = verificationState.third,
            detectFailures = recoveryState.first,
            autoReconnect = recoveryState.second,
            handshakeTimeoutSeconds = recoveryState.third,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ConnectionVerificationUiState(),
    )

    fun setMode(mode: ConnectionVerificationMode) = viewModelScope.launch {
        settings.setConnectionVerificationMode(mode)
    }

    fun setRequireVerification(value: Boolean) = viewModelScope.launch {
        settings.setConnectionVerificationRequired(value)
    }

    fun setRequirePreflight(value: Boolean) = viewModelScope.launch {
        settings.setConnectionPreflightRequired(value)
    }

    fun setDetectFailures(value: Boolean) = viewModelScope.launch {
        settings.setConnectionFailureDetection(value)
    }

    fun setAutoReconnect(value: Boolean) = viewModelScope.launch {
        settings.setConnectionAutoReconnect(value)
    }

    fun setHandshakeTimeoutSeconds(value: Int) = viewModelScope.launch {
        settings.setHandshakeReconnectTimeoutSeconds(value)
    }
}
