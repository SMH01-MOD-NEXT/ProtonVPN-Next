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
import ru.protonmod.next.data.local.SettingsManager

data class IpRotationSettingsUiState(
    val enabled: Boolean = false,
    val intervalMinutes: Int = SettingsManager.DEFAULT_IP_ROTATION_INTERVAL_MINUTES,
    val keepCountry: Boolean = true,
)

@HiltViewModel
class IpRotationSettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
) : ViewModel() {
    val uiState: StateFlow<IpRotationSettingsUiState> = combine(
        settingsManager.ipRotationEnabled,
        settingsManager.ipRotationIntervalMinutes,
        settingsManager.ipRotationKeepCountry,
    ) { enabled, interval, keepCountry ->
        IpRotationSettingsUiState(enabled, interval, keepCountry)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IpRotationSettingsUiState())

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsManager.setIpRotationEnabled(enabled)
    }

    fun setIntervalMinutes(minutes: Int) = viewModelScope.launch {
        settingsManager.setIpRotationIntervalMinutes(minutes)
    }

    fun setKeepCountry(keepCountry: Boolean) = viewModelScope.launch {
        settingsManager.setIpRotationKeepCountry(keepCountry)
    }
}
