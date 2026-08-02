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

package ru.protonmod.next.ui.screens.netshield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.netshield.LocalNetShield
import ru.protonmod.next.netshield.NetShieldCategory
import ru.protonmod.next.netshield.NetShieldLevel
import ru.protonmod.next.netshield.NetShieldListState
import ru.protonmod.next.netshield.NetShieldSourceConfig
import ru.protonmod.next.netshield.NetShieldSources
import javax.inject.Inject

data class NetShieldSettingsUiState(
    val level: NetShieldLevel = NetShieldLevel.DISABLED,
    val lists: NetShieldListState = NetShieldListState(),
    val sources: NetShieldSourceConfig = NetShieldSourceConfig(),
) {
    /** Effective download URL per category, used to describe the active provider in the UI. */
    fun sourceUrl(category: NetShieldCategory): String = NetShieldSources.resolve(category, sources)

    fun isDefaultSource(category: NetShieldCategory): Boolean =
        category !in sources.presetIds && category !in sources.customUrls
}

@HiltViewModel
class NetShieldSettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val localNetShield: LocalNetShield,
) : ViewModel() {
    init {
        viewModelScope.launch {
            if (localNetShield.needsListUpdate()) localNetShield.updateLists()
        }
    }

    val uiState: StateFlow<NetShieldSettingsUiState> = combine(
        settingsManager.netShieldLevel,
        localNetShield.listState,
        localNetShield.sourceConfig,
    ) { level, lists, sources -> NetShieldSettingsUiState(level, lists, sources) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetShieldSettingsUiState())

    fun setLevel(level: NetShieldLevel) {
        viewModelScope.launch {
            settingsManager.setNetShieldLevel(level)
            if (level.enabled && localNetShield.listState.value.domainCount == 0 && !localNetShield.listState.value.isUpdating) {
                localNetShield.updateLists()
            }
        }
    }

    fun updateLists() {
        viewModelScope.launch { localNetShield.updateLists() }
    }

    /** Adds pasted or file-provided rules to the user's own blocklist. */
    fun importCustomFilters(content: String, replace: Boolean = false) {
        viewModelScope.launch { localNetShield.importCustomFilters(content, replace) }
    }

    fun importCustomFiltersFromUrl(url: String, replace: Boolean = false) {
        viewModelScope.launch { localNetShield.importCustomFiltersFromUrl(url, replace) }
    }

    fun clearCustomFilters() {
        viewModelScope.launch { localNetShield.clearCustomFilters() }
    }

    fun setCategoryPreset(category: NetShieldCategory, presetId: String) {
        viewModelScope.launch {
            localNetShield.setCategoryPreset(category, presetId)
            localNetShield.updateLists()
        }
    }

    fun setCategoryUrl(category: NetShieldCategory, url: String) {
        viewModelScope.launch {
            localNetShield.setCategoryUrl(category, url)
            localNetShield.updateLists()
        }
    }

    fun resetCategorySource(category: NetShieldCategory) {
        viewModelScope.launch {
            localNetShield.resetCategorySource(category)
            localNetShield.updateLists()
        }
    }

    fun applyPresetToAll(presetId: String) {
        viewModelScope.launch {
            localNetShield.applyPresetToAll(presetId)
            localNetShield.updateLists()
        }
    }

    fun resetAllSources() {
        viewModelScope.launch {
            localNetShield.resetAllSources()
            localNetShield.updateLists()
        }
    }
}
