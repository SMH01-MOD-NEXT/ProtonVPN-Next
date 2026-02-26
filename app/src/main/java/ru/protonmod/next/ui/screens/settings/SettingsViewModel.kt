/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

data class SettingsUiState(
    val killSwitchEnabled: Boolean = false,
    val autoConnectEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val splitTunnelingEnabled: Boolean = false,
    val excludedApps: Set<String> = emptySet(),
    val excludedIps: Set<String> = emptySet(),
    val isVpnConnected: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val amneziaVpnManager: AmneziaVpnManager,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsManager.killSwitchEnabled,
        settingsManager.autoConnectEnabled,
        settingsManager.notificationsEnabled,
        settingsManager.splitTunnelingEnabled,
        settingsManager.excludedApps,
        settingsManager.excludedIps,
        amneziaVpnManager.tunnelState
    ) { args: Array<Any?> ->
        SettingsUiState(
            killSwitchEnabled = args[0] as Boolean,
            autoConnectEnabled = args[1] as Boolean,
            notificationsEnabled = args[2] as Boolean,
            splitTunnelingEnabled = args[3] as Boolean,
            excludedApps = args[4] as Set<String>,
            excludedIps = args[5] as Set<String>,
            isVpnConnected = args[6] == Tunnel.State.UP
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setKillSwitch(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setKillSwitch(enabled)
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAutoConnect(enabled)
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setNotifications(enabled)
        }
    }

    fun setSplitTunneling(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSplitTunnelingEnabled(enabled)
        }
    }

    fun addExcludedApp(packageName: String) {
        viewModelScope.launch {
            val current = uiState.value.excludedApps
            settingsManager.setExcludedApps(current + packageName)
        }
    }

    fun removeExcludedApp(packageName: String) {
        viewModelScope.launch {
            val current = uiState.value.excludedApps
            settingsManager.setExcludedApps(current - packageName)
        }
    }

    fun addExcludedIp(ip: String) {
        viewModelScope.launch {
            val current = uiState.value.excludedIps
            settingsManager.setExcludedIps(current + ip)
        }
    }

    fun removeExcludedIp(ip: String) {
        viewModelScope.launch {
            val current = uiState.value.excludedIps
            settingsManager.setExcludedIps(current - ip)
        }
    }
}
