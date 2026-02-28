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

// Represents a saved obfuscation configuration
data class ObfuscationProfile(
    val id: String,
    val name: String,
    val isReadOnly: Boolean,
    val jc: Int,
    val jmin: Int,
    val jmax: Int,
    val s1: Int,
    val s2: Int,
    val h1: String,
    val h2: String,
    val h3: String,
    val h4: String,
    val i1: String
) {
    companion object {
        fun getStandardProfile(translatedName: String = "Standard Proton VPN-Next Config") = ObfuscationProfile(
            id = "standard_1",
            name = translatedName,
            isReadOnly = true,
            jc = 3, jmin = 1, jmax = 3, // Proton VPN-Next default bypass values
            s1 = 0, s2 = 0,
            h1 = "1", h2 = "2", h3 = "3", h4 = "4",
            i1 = SettingsManager.DEFAULT_I1
        )
    }
}

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
    val isVpnConnected: Boolean = false,

    // Obfuscation configuration state
    val isObfuscationEnabled: Boolean = false,
    val customObfuscationProfiles: List<ObfuscationProfile> = emptyList(),
    val selectedProfileId: String = "standard_1"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val amneziaVpnManager: AmneziaVpnManager,
    private val settingsManager: SettingsManager
) : ViewModel() {

    // Using array combine to bypass the 5 Flow limit in coroutines
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
        amneziaVpnManager.tunnelState,
        // Added properties for obfuscation configs
        settingsManager.obfuscationEnabled,
        settingsManager.customProfiles,
        settingsManager.selectedProfileId
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
            isVpnConnected = args[17] == Tunnel.State.UP,
            isObfuscationEnabled = args[18] as Boolean,
            customObfuscationProfiles = args[19] as List<ObfuscationProfile>,
            selectedProfileId = args[20] as String
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

    // Master switch for obfuscation
    fun setObfuscationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setObfuscationEnabled(enabled)
        }
    }

    // Used strictly to update currently active params
    fun setAwgParams(
        jc: Int, jmin: Int, jmax: Int, s1: Int, s2: Int,
        h1: String, h2: String, h3: String, h4: String, i1: String
    ) {
        viewModelScope.launch {
            settingsManager.setAwgParams(jc, jmin, jmax, s1, s2, h1, h2, h3, h4, i1)
        }
    }

    // Selects a profile and applies its settings immediately
    fun selectObfuscationProfile(profile: ObfuscationProfile) {
        viewModelScope.launch {
            settingsManager.setSelectedProfileId(profile.id)
            setAwgParams(
                jc = profile.jc, jmin = profile.jmin, jmax = profile.jmax,
                s1 = profile.s1, s2 = profile.s2,
                h1 = profile.h1, h2 = profile.h2, h3 = profile.h3, h4 = profile.h4,
                i1 = profile.i1
            )
        }
    }

    // Upserts a profile (add new or update existing)
    fun saveObfuscationProfile(profile: ObfuscationProfile) {
        viewModelScope.launch {
            val currentList = uiState.value.customObfuscationProfiles
            val index = currentList.indexOfFirst { it.id == profile.id }
            val newList = if (index != -1) {
                currentList.toMutableList().apply { this[index] = profile }
            } else {
                currentList + profile
            }
            settingsManager.saveCustomProfiles(newList)
            selectObfuscationProfile(profile) // Auto-select on save
        }
    }

    fun resetToStandard() {
        val standard = ObfuscationProfile.getStandardProfile()
        selectObfuscationProfile(standard)
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