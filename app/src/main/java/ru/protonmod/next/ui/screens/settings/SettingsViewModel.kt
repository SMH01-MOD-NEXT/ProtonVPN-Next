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
    val vpnPort: Int = 1194,
    val awgJc: Int = 3,
    val awgJmin: Int = 1,
    val awgJmax: Int = 3,
    val awgS1: Int = 0,
    val awgS2: Int = 0,
    val awgH1: String = "1",
    val awgH2: String = "2",
    val awgH3: String = "3",
    val awgH4: String = "4",
    val awgI1: String = SettingsManager.DEFAULT_I1,
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
        settingsManager.vpnPort,
        settingsManager.awgJc,
        settingsManager.awgJmin,
        settingsManager.awgJmax,
        settingsManager.awgS1,
        settingsManager.awgS2,
        settingsManager.awgH1,
        settingsManager.awgH2,
        settingsManager.awgH3,
        settingsManager.awgH4,
        settingsManager.awgI1,
        amneziaVpnManager.tunnelState
    ) { args: Array<Any?> ->
        SettingsUiState(
            killSwitchEnabled = args[0] as Boolean,
            autoConnectEnabled = args[1] as Boolean,
            notificationsEnabled = args[2] as Boolean,
            splitTunnelingEnabled = args[3] as Boolean,
            excludedApps = args[4] as Set<String>,
            excludedIps = args[5] as Set<String>,
            vpnPort = args[6] as Int,
            awgJc = args[7] as Int,
            awgJmin = args[8] as Int,
            awgJmax = args[9] as Int,
            awgS1 = args[10] as Int,
            awgS2 = args[11] as Int,
            awgH1 = args[12] as String,
            awgH2 = args[13] as String,
            awgH3 = args[14] as String,
            awgH4 = args[15] as String,
            awgI1 = args[16] as String,
            isVpnConnected = args[17] == Tunnel.State.UP
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

    fun setVpnPort(port: Int) {
        viewModelScope.launch {
            settingsManager.setVpnPort(port)
        }
    }

    fun setAwgParams(
        jc: Int, jmin: Int, jmax: Int, s1: Int, s2: Int,
        h1: String, h2: String, h3: String, h4: String, i1: String
    ) {
        viewModelScope.launch {
            settingsManager.setAwgParams(jc, jmin, jmax, s1, s2, h1, h2, h3, h4, i1)
        }
    }

    fun resetToStandard() {
        viewModelScope.launch {
            settingsManager.setAwgParams(3, 1, 3, 0, 0, "1", "2", "3", "4", SettingsManager.DEFAULT_I1)
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
