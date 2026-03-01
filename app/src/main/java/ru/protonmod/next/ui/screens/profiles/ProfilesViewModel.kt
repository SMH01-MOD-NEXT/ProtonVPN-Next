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

package ru.protonmod.next.ui.screens.profiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.R
import ru.protonmod.next.data.cache.ServersCacheManager
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.ui.screens.countries.CityDisplayItem
import ru.protonmod.next.ui.screens.countries.CountryDisplayItem
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serversCacheManager: ServersCacheManager,
    private val sessionDao: SessionDao,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val connectedServerState: ConnectedServerState,
    private val profileDao: ProfileDao,
    private val settingsManager: SettingsManager
) : ViewModel() {

    companion object {
        private const val TAG = "ProfilesViewModel"
    }

    val profiles: StateFlow<List<VpnProfileUiModel>> = profileDao.getAllProfilesFlow()
        .map { entities ->
            entities.map { entity ->
                VpnProfileUiModel(
                    id = entity.id,
                    name = entity.name,
                    protocol = entity.protocol,
                    port = entity.port,
                    isObfuscationEnabled = entity.isObfuscationEnabled,
                    obfuscationProfileId = entity.obfuscationProfileId,
                    autoOpenUrl = entity.autoOpenUrl,
                    targetServerId = entity.targetServerId,
                    targetCountry = entity.targetCountry,
                    targetCity = entity.targetCity
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Changed from List<String> to List<CountryDisplayItem> to support load indicators
    private val _countries = MutableStateFlow<List<CountryDisplayItem>>(emptyList())
    val countries: StateFlow<List<CountryDisplayItem>> = _countries.asStateFlow()

    init {
        loadCountries()
    }

    private fun loadCountries() {
        viewModelScope.launch {
            _countries.value = getAvailableCountries()
        }
    }

    fun saveProfile(uiModel: VpnProfileUiModel) {
        viewModelScope.launch {
            val entity = VpnProfileEntity(
                id = uiModel.id,
                name = uiModel.name,
                protocol = uiModel.protocol,
                port = uiModel.port,
                isObfuscationEnabled = uiModel.isObfuscationEnabled,
                obfuscationProfileId = uiModel.obfuscationProfileId,
                autoOpenUrl = uiModel.autoOpenUrl,
                targetServerId = uiModel.targetServerId,
                targetCountry = uiModel.targetCountry,
                targetCity = uiModel.targetCity
            )
            profileDao.insertProfile(entity)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileDao.deleteProfileById(id)
        }
    }

    suspend fun getProfileById(id: String): VpnProfileUiModel? {
        return profileDao.getProfileById(id)?.let { entity ->
            VpnProfileUiModel(
                id = entity.id,
                name = entity.name,
                protocol = entity.protocol,
                port = entity.port,
                isObfuscationEnabled = entity.isObfuscationEnabled,
                obfuscationProfileId = entity.obfuscationProfileId,
                autoOpenUrl = entity.autoOpenUrl,
                targetServerId = entity.targetServerId,
                targetCountry = entity.targetCountry,
                targetCity = entity.targetCity
            )
        }
    }

    fun connectWithProfile(profile: VpnProfileUiModel) {
        viewModelScope.launch {
            val session = sessionDao.getSession()
            if (session == null) {
                Log.e(TAG, "Cannot connect: No session found")
                return@launch
            }

            val servers = serversCacheManager.getCachedServers()
            if (servers.isEmpty()) {
                Log.e(TAG, "Cannot connect: Server list is empty")
                return@launch
            }

            val targetServer = findBestServerForProfile(profile, servers)
            if (targetServer == null) {
                Log.e(TAG, "Cannot connect: No suitable server found for profile")
                return@launch
            }

            val physicalServer = targetServer.servers.firstOrNull { it.status == 1 }
            if (physicalServer == null) {
                Log.e(TAG, "Cannot connect: Selected server is currently unavailable.")
                return@launch
            }

            var obfuscationParams: AmneziaVpnManager.ObfuscationParams? = null
            if (profile.isObfuscationEnabled && profile.obfuscationProfileId != null) {
                val customProfiles = settingsManager.customProfiles.first()
                val standardProfileName = context.getString(R.string.obfuscation_config_standard)
                val selectedConfig = customProfiles.find { it.id == profile.obfuscationProfileId }
                    ?: if (profile.obfuscationProfileId == "standard_1") ObfuscationProfile.getStandardProfile(standardProfileName) else null

                selectedConfig?.let {
                    obfuscationParams = AmneziaVpnManager.ObfuscationParams(
                        jc = it.jc, jmin = it.jmin, jmax = it.jmax,
                        s1 = it.s1, s2 = it.s2,
                        h1 = it.h1, h2 = it.h2, h3 = it.h3, h4 = it.h4,
                        i1 = it.i1
                    )
                }
            }

            connectedServerState.setConnectedServer(targetServer)
            val tunnelState = amneziaVpnManager.tunnelState.value
            val isConnecting = amneziaVpnManager.isConnecting.value

            if (tunnelState == Tunnel.State.UP || isConnecting) {
                amneziaVpnManager.reconnect(
                    targetServer.id,
                    physicalServer,
                    session,
                    overridePort = profile.port,
                    overrideObfuscation = profile.isObfuscationEnabled,
                    obfuscationParams = obfuscationParams
                )
            } else {
                amneziaVpnManager.connect(
                    targetServer.id,
                    physicalServer,
                    session,
                    overridePort = profile.port,
                    overrideObfuscation = profile.isObfuscationEnabled,
                    obfuscationParams = obfuscationParams
                )
            }

            if (!profile.autoOpenUrl.isNullOrEmpty()) {
                handleAutoOpenUrl(profile.autoOpenUrl)
            }
        }
    }

    private fun findBestServerForProfile(
        profile: VpnProfileUiModel,
        allServers: List<LogicalServer>
    ): LogicalServer? {
        if (profile.targetServerId != null) {
            return allServers.find { it.id == profile.targetServerId }
        }

        if (profile.targetCity != null && profile.targetCountry != null) {
            val cityServers = allServers.filter { it.exitCountry == profile.targetCountry && it.city == profile.targetCity }
            if (cityServers.isNotEmpty()) {
                return cityServers.minByOrNull { it.averageLoad }
            }
        }

        if (profile.targetCountry != null) {
            val countryServers = allServers.filter { it.exitCountry == profile.targetCountry }
            if (countryServers.isNotEmpty()) {
                return countryServers.minByOrNull { it.averageLoad }
            }
        }

        return allServers.minByOrNull { it.averageLoad }
    }

    private fun handleAutoOpenUrl(url: String?) {
        if (url.isNullOrEmpty()) return

        viewModelScope.launch {
            Log.d(TAG, "Waiting for VPN to be UP before opening URL: $url")
            try {
                // Wait for the tunnel to reach UP state with a 20s timeout
                withTimeout(20000) {
                    amneziaVpnManager.tunnelState.first { it == Tunnel.State.UP }
                }

                // Extra small delay to ensure routing is established
                delay(800)

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Connect & Go: URL opened successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle Connect & Go", e)
                // Fallback: try opening anyway if it took too long but we are still attempting
                if (amneziaVpnManager.tunnelState.value == Tunnel.State.UP) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Now computes average load for countries to be displayed in the UI
    suspend fun getAvailableCountries(): List<CountryDisplayItem> {
        return serversCacheManager.getCachedServers()
            .groupBy { it.exitCountry }
            .map { (countryCode, servers) ->
                val avgLoad = if (servers.isEmpty()) 0 else servers.map { it.averageLoad }.average().toInt()
                CountryDisplayItem(code = countryCode, averageLoad = avgLoad)
            }
            .sortedBy { it.code }
    }

    // Now computes average load for cities to be displayed in the UI
    suspend fun getCitiesForCountry(countryCode: String): List<CityDisplayItem> {
        return serversCacheManager.getCachedServers()
            .filter { it.exitCountry == countryCode }
            .groupBy { it.city }
            .map { (cityName, servers) ->
                val avgLoad = if (servers.isEmpty()) 0 else servers.map { it.averageLoad }.average().toInt()
                CityDisplayItem(name = cityName, averageLoad = avgLoad)
            }
            .sortedBy { it.name }
    }

    suspend fun getServersForCity(countryCode: String, city: String): List<LogicalServer> {
        return serversCacheManager.getCachedServers()
            .filter { it.exitCountry == countryCode && it.city == city }
            .sortedBy { it.name }
    }

    val customObfuscationConfigs: StateFlow<List<ObfuscationProfile>> = settingsManager.customProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveObfuscationProfile(profile: ObfuscationProfile) {
        viewModelScope.launch {
            val current = settingsManager.customProfiles.first()
            val index = current.indexOfFirst { it.id == profile.id }
            val updated = if (index != -1) {
                current.toMutableList().apply { this[index] = profile }
            } else {
                current + profile
            }
            settingsManager.saveCustomProfiles(updated)
        }
    }

    fun deleteObfuscationProfile(id: String) {
        viewModelScope.launch {
            val current = settingsManager.customProfiles.first()
            val updated = current.filter { it.id != id }
            settingsManager.saveCustomProfiles(updated)
        }
    }
}