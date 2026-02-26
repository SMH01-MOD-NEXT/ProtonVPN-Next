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
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.vpn.IpSubnetCalculator
import javax.inject.Inject

data class IpEntry(
    val ip: String,
    val isValid: Boolean = true
)

data class SplitTunnelingIpsUiState(
    val ips: List<IpEntry> = emptyList(),
    val newIpInput: String = ""
)

@HiltViewModel
class SplitTunnelingIpsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val uiState: StateFlow<SplitTunnelingIpsUiState> = combine(
        settingsManager.excludedIps
    ) { args ->
        val excludedIps = args[0]
        SplitTunnelingIpsUiState(
            ips = excludedIps.map { IpEntry(ip = it, isValid = true) }
                .sortedBy { it.ip }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SplitTunnelingIpsUiState()
    )

    fun addIp(ip: String) {
        val trimmedIp = ip.trim()
        if (isValidIp(trimmedIp) && IpSubnetCalculator.isValidIpOrCidr(trimmedIp)) {
            viewModelScope.launch {
                val current = settingsManager.excludedIps.stateIn(viewModelScope).value
                val normalizedIp = IpSubnetCalculator.normalizeIp(trimmedIp)
                if (normalizedIp !in current) {
                    settingsManager.setExcludedIps(current + normalizedIp)
                }
            }
        }
    }

    fun removeIp(ip: String) {
        viewModelScope.launch {
            val current = settingsManager.excludedIps.stateIn(viewModelScope).value
            settingsManager.setExcludedIps(current - ip)
        }
    }

    fun removeAll() {
        viewModelScope.launch {
            settingsManager.setExcludedIps(emptySet())
        }
    }

    private fun isValidIp(ip: String): Boolean {
        if (ip.isEmpty()) return false

        // Check if it's a valid IPv4 address or CIDR notation
        val ipPattern = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(/\\d{1,2})?$")
        if (!ipPattern.matches(ip)) return false

        // Validate IPv4 octets
        val parts = ip.split("/")[0].split(".")
        if (parts.size != 4) return false

        for (part in parts) {
            try {
                val octet = part.toInt()
                if (octet < 0 || octet > 255) return false
            } catch (_: Exception) {
                return false
            }
        }

        // Check CIDR prefix if present
        if (ip.contains("/")) {
            try {
                val prefix = ip.split("/")[1].toInt()
                if (prefix < 0 || prefix > 32) return false
            } catch (_: Exception) {
                return false
            }
        }

        return true
    }
}



