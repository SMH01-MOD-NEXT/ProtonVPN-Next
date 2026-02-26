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
import android.content.pm.PackageInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.protonmod.next.data.local.SettingsManager
import javax.inject.Inject

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSelected: Boolean
)

data class SplitTunnelingAppsUiState(
    val apps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SplitTunnelingAppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val uiState: StateFlow<SplitTunnelingAppsUiState> = combine(
        settingsManager.excludedApps
    ) { args ->
        val excludedApps = args[0]
        val installedApps = getInstalledApps(excludedApps)

        SplitTunnelingAppsUiState(
            apps = installedApps,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SplitTunnelingAppsUiState()
    )

    private suspend fun getInstalledApps(excludedApps: Set<String>): List<AppInfo> =
        withContext(Dispatchers.IO) {
            try {
                val packages = context.packageManager.getInstalledPackages(0)
                packages
                    .filter { packageInfo ->
                        val appInfo = packageInfo.applicationInfo
                        appInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0 // Only non-system apps
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
                            appName = appLabel,
                            isSelected = packageInfo.packageName in excludedApps
                        )
                    }
                    .sortedBy { it.appName.lowercase() }
            } catch (_: Exception) {
                emptyList()
            }
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
            val allPackages = uiState.value.apps.map { it.packageName }.toSet()
            settingsManager.setExcludedApps(allPackages)
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            settingsManager.setExcludedApps(emptySet())
        }
    }
}
