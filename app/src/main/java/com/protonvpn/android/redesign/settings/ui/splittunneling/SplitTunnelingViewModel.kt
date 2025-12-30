/*
 * Copyright (c) 2025 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.redesign.settings.ui.splittunneling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.Setting
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.ui.settings.SplitTunnelingAppsViewModelHelper
import com.protonvpn.android.utils.isIPv6
import com.protonvpn.android.utils.isValidIp
import com.protonvpn.android.vpn.usecases.IsIPv6FeatureFlagEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

@HiltViewModel
class SplitTunnelingViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    installedAppsProvider: InstalledAppsProvider,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
    private val isIPv6FeatureFlagEnabled: IsIPv6FeatureFlagEnabled,
) : ViewModel() {

    // --- Common State ---
    private val _mode = MutableStateFlow(SplitTunnelingMode.EXCLUDE_ONLY)
    private val _currentTab = MutableStateFlow(SplitTunnelingTab.APPS)

    // --- Apps State ---
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _searchQuery = MutableStateFlow("")
    private val helper = SplitTunnelingAppsViewModelHelper(
        viewModelScope,
        dispatcherProvider,
        installedAppsProvider,
        _selectedPackages,
        forTv = false,
    )

    // --- IPs State ---
    private val _ipAddresses = MutableStateFlow<List<String>>(emptyList())
    private val _ipInputError = MutableStateFlow<Int?>(null)

    // --- Events ---
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // --- Helper Flow Grouping to avoid "combine" argument limit (max 5) ---
    private val uiInputs = combine(_mode, _currentTab, _searchQuery) { mode, tab, query ->
        Triple(mode, tab, query)
    }

    // --- Combined UI State ---
    val viewState: StateFlow<SplitTunnelingUiState> = combine(
        uiInputs,
        helper.viewState,
        _ipAddresses,
        _ipInputError
    ) { (mode, tab, query), appsState, ips, ipError ->

        // Prepare Apps Data
        val appsContent = when (appsState) {
            is SplitTunnelingAppsViewModelHelper.ViewState.Loading -> AppsUiState.Loading
            is SplitTunnelingAppsViewModelHelper.ViewState.Content -> {
                val filteredSelected = appsState.selectedApps.filter { it.containsQuery(query) }
                val filteredRegular = appsState.availableRegularApps.filter { it.containsQuery(query) }

                val systemAppsState = when (val sys = appsState.availableSystemApps) {
                    is SplitTunnelingAppsViewModelHelper.SystemAppsState.Content ->
                        SplitTunnelingSystemAppsState.Loaded(sys.apps.filter { it.containsQuery(query) })
                    is SplitTunnelingAppsViewModelHelper.SystemAppsState.Loading ->
                        SplitTunnelingSystemAppsState.Loading
                    is SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded ->
                        SplitTunnelingSystemAppsState.NotLoaded
                }

                AppsUiState.Content(
                    selectedApps = filteredSelected,
                    availableApps = filteredRegular,
                    systemAppsState = systemAppsState
                )
            }
        }

        // Prepare IPs Data
        val ipsContent = IpsUiState(
            items = ips,
            inputError = ipError
        )

        SplitTunnelingUiState(
            mode = mode,
            currentTab = tab,
            searchQuery = query,
            appsState = appsContent,
            ipsState = ipsContent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SplitTunnelingUiState.Loading)

    init {
        viewModelScope.launch {
            val settings = userSettingsManager.rawCurrentUserSettingsFlow.first().splitTunneling
            _mode.value = settings.mode

            // Load Apps
            _selectedPackages.value = when (settings.mode) {
                SplitTunnelingMode.INCLUDE_ONLY -> settings.includedApps
                SplitTunnelingMode.EXCLUDE_ONLY -> settings.excludedApps
            }.toHashSet()

            // Load IPs
            _ipAddresses.value = when (settings.mode) {
                SplitTunnelingMode.INCLUDE_ONLY -> settings.includedIps
                SplitTunnelingMode.EXCLUDE_ONLY -> settings.excludedIps
            }
        }
    }

    // --- Actions ---

    fun onTabSelected(tab: SplitTunnelingTab) {
        _currentTab.value = tab
    }

    fun onModeChanged(newMode: SplitTunnelingMode) {
        _mode.value = newMode
        // When mode changes, we need to reload the corresponding lists from storage
        viewModelScope.launch {
            val settings = userSettingsManager.rawCurrentUserSettingsFlow.first().splitTunneling

            _selectedPackages.value = when (newMode) {
                SplitTunnelingMode.INCLUDE_ONLY -> settings.includedApps
                SplitTunnelingMode.EXCLUDE_ONLY -> settings.excludedApps
            }.toHashSet()

            _ipAddresses.value = when (newMode) {
                SplitTunnelingMode.INCLUDE_ONLY -> settings.includedIps
                SplitTunnelingMode.EXCLUDE_ONLY -> settings.excludedIps
            }

            // We also need to save the new Mode itself
            saveAppsChanges()
            saveIpChanges()
        }
    }

    // --- Apps Actions ---

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun toggleLoadSystemApps() {
        helper.toggleLoadSystemApps()
    }

    fun addApp(item: LabeledItem) {
        _selectedPackages.update { it + item.id }
        saveAppsChanges()
    }

    fun removeApp(item: LabeledItem) {
        _selectedPackages.update { it - item.id }
        saveAppsChanges()
    }

    // --- IP Actions ---

    fun validateAndAddIp(ip: String) {
        val trimmedIp = ip.trim()
        viewModelScope.launch {
            if (!trimmedIp.isValidIp(isIPv6FeatureFlagEnabled())) {
                _ipInputError.value = R.string.inputIpAddressErrorInvalid
                return@launch
            }

            if (_ipAddresses.value.contains(trimmedIp)) {
                _ipInputError.value = when (_mode.value) {
                    SplitTunnelingMode.INCLUDE_ONLY -> R.string.settings_split_tunneling_already_included
                    SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settings_split_tunneling_already_excluded
                }
                return@launch
            }

            if (shouldDisplayIPv6SettingDialog(_mode.value, trimmedIp)) {
                _events.emit(Event.ShowIPv6EnableDialog)
            }

            _ipInputError.value = null
            _ipAddresses.update { it + trimmedIp }
            _events.emit(Event.IpAdded)
            saveIpChanges()
        }
    }

    fun removeIp(ip: String) {
        _ipAddresses.update { it - ip }
        saveIpChanges()
    }

    fun clearIpError() {
        _ipInputError.value = null
    }

    fun onEnableIPv6() {
        viewModelScope.launch {
            userSettingsManager.update { current -> current.copy(ipV6Enabled = true) }
            _events.emit(Event.ShowIPv6EnabledToast)
        }
    }

    // --- Persistence ---

    private fun saveAppsChanges() {
        ProtonLogger.logUiSettingChange(Setting.SPLIT_TUNNEL_APPS, "settings")
        viewModelScope.launch {
            userSettingsManager.updateSplitTunnelApps(_selectedPackages.value.toList(), _mode.value)
        }
    }

    private fun saveIpChanges() {
        ProtonLogger.logUiSettingChange(Setting.SPLIT_TUNNEL_IPS, "settings")
        viewModelScope.launch {
            userSettingsManager.updateExcludedIps(_ipAddresses.value, _mode.value)
        }
    }

    // --- Helpers ---

    private fun LabeledItem.containsQuery(query: String): Boolean {
        return label.contains(query, ignoreCase = true)
    }

    private suspend fun shouldDisplayIPv6SettingDialog(mode: SplitTunnelingMode, ipText: String): Boolean =
        mode == SplitTunnelingMode.INCLUDE_ONLY && ipText.isIPv6()
                && isIPv6FeatureFlagEnabled() && !userSettingsManager.rawCurrentUserSettingsFlow.first().ipV6Enabled

    // --- Definitions ---

    sealed class Event {
        object ShowIPv6EnableDialog : Event()
        object ShowIPv6EnabledToast : Event()
        object IpAdded : Event()
    }
}

enum class SplitTunnelingTab { APPS, IPS }

data class SplitTunnelingUiState(
    val mode: SplitTunnelingMode,
    val currentTab: SplitTunnelingTab,
    val searchQuery: String,
    val appsState: AppsUiState,
    val ipsState: IpsUiState
) {
    companion object {
        val Loading = SplitTunnelingUiState(
            SplitTunnelingMode.EXCLUDE_ONLY, SplitTunnelingTab.APPS, "", AppsUiState.Loading, IpsUiState(emptyList(), null)
        )
    }
}

sealed class AppsUiState {
    object Loading : AppsUiState()
    data class Content(
        val selectedApps: List<LabeledItem>,
        val availableApps: List<LabeledItem>,
        val systemAppsState: SplitTunnelingSystemAppsState
    ) : AppsUiState()
}

data class IpsUiState(
    val items: List<String>,
    val inputError: Int?
)

sealed class SplitTunnelingSystemAppsState {
    object NotLoaded : SplitTunnelingSystemAppsState()
    object Loading : SplitTunnelingSystemAppsState()
    data class Loaded(val apps: List<LabeledItem>) : SplitTunnelingSystemAppsState()
}