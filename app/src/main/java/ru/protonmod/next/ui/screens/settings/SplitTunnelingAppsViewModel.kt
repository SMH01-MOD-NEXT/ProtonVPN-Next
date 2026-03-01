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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SettingsManager
import javax.inject.Inject

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSelected: Boolean = false
)

data class SplitTunnelingAppsUiState(
    val selectedApps: List<AppInfo> = emptyList(),
    val availableApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class SplitTunnelingAppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) : ViewModel() {

    // Cache all apps in memory so we don't query PackageManager on every toggle
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<SplitTunnelingAppsUiState> = combine(
        _allApps,
        settingsManager.excludedApps,
        _searchQuery
    ) { allApps, excludedApps, query ->
        val isLoading = allApps.isEmpty()

        // Filter apps by search query
        val filteredApps = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

        // Split into Selected and Available lists (like original Proton app)
        val selected = filteredApps
            .filter { it.packageName in excludedApps }
            .map { it.copy(isSelected = true) }
            .sortedBy { it.appName.lowercase() }

        val available = filteredApps
            .filter { it.packageName !in excludedApps }
            .map { it.copy(isSelected = false) }
            .sortedBy { it.appName.lowercase() }

        SplitTunnelingAppsUiState(
            selectedApps = selected,
            availableApps = available,
            searchQuery = query,
            isLoading = isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SplitTunnelingAppsUiState()
    )

    init {
        // Fetch installed apps once on init
        viewModelScope.launch(Dispatchers.IO) {
            _allApps.value = fetchInstalledApps()
        }
    }

    private fun fetchInstalledApps(): List<AppInfo> {
        return try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledPackages(0)
            }

            packages
                .filter { packageInfo ->
                    val appInfo = packageInfo.applicationInfo
                    appInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0
                }
                .mapNotNull { packageInfo ->
                    val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                    val appLabel = try {
                        context.packageManager.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        packageInfo.packageName
                    }
                    AppInfo(
                        packageName = packageInfo.packageName,
                        appName = appLabel
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleApp(packageName: String, isSelected: Boolean) {
        viewModelScope.launch {
            val current = settingsManager.excludedApps.stateIn(viewModelScope).value
            val updated = if (isSelected) {
                current + packageName
            } else {
                current - packageName
            }
            settingsManager.setExcludedApps(updated)
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            val allPackages = _allApps.value.map { it.packageName }.toSet()
            settingsManager.setExcludedApps(allPackages)
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            settingsManager.setExcludedApps(emptySet())
        }
    }
}