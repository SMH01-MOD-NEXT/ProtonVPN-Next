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
import ru.protonmod.next.utils.ProtonLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ru.protonmod.next.vpn.VpnTunnelState
import ru.protonmod.next.R
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.ServerLoadDisplayMode
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
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val connectedServerState: ConnectedServerState,
    private val profileDao: ProfileDao,
    private val settingsManager: SettingsManager
) : ViewModel() {

    companion object {
        private const val TAG = "ProfilesViewModel"
    }

    val profiles: StateFlow<List<VpnProfileUiModel>> = combine(
        profileDao.getAllProfilesFlow(),
        vpnRepository.getServersFlow()
    ) { entities, servers ->
        entities.map { entity ->
            val matchingServer = servers.find { it.id == entity.targetServerId }
            val serverName = matchingServer?.name
            val localizedCity = matchingServer?.localizedCity
                ?: if (entity.targetCity != null) {
                    servers.find { it.exitCountry == entity.targetCountry && it.city == entity.targetCity }?.localizedCity
                } else null
            VpnProfileUiModel(
                id = entity.id,
                name = entity.name,
                protocol = entity.protocol,
                port = entity.port,
                isObfuscationEnabled = entity.isObfuscationEnabled,
                obfuscationProfileId = entity.obfuscationProfileId,
                autoOpenUrl = entity.autoOpenUrl,
                targetServerId = entity.targetServerId,
                targetServerName = serverName,
                targetCountry = entity.targetCountry,
                targetCity = entity.targetCity,
                localizedCity = localizedCity
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _countries = MutableStateFlow<List<CountryDisplayItem>>(emptyList())
    val countries: StateFlow<List<CountryDisplayItem>> = _countries.asStateFlow()

    init {
        observeServersForCountries()
    }

    private fun observeServersForCountries() {
        viewModelScope.launch {
            vpnRepository.getServersFlow().collect { servers ->
                if (servers.isNotEmpty()) {
                    _countries.value = servers
                        .groupBy { it.exitCountry }
                        .map { (countryCode, countryServers) ->
                            val avgLoad = if (countryServers.isEmpty()) 0 else countryServers.map { it.averageLoad }.average().toInt()
                            CountryDisplayItem(code = countryCode, averageLoad = avgLoad)
                        }
                        .sortedBy { it.code }
                }
            }
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
            val servers = vpnRepository.getCachedServers()
            val matchingServer = servers.find { it.id == entity.targetServerId }
            val serverName = matchingServer?.name
            val localizedCity = matchingServer?.localizedCity
                ?: if (entity.targetCity != null) {
                    servers.find { it.exitCountry == entity.targetCountry && it.city == entity.targetCity }?.localizedCity
                } else null

            VpnProfileUiModel(
                id = entity.id,
                name = entity.name,
                protocol = entity.protocol,
                port = entity.port,
                isObfuscationEnabled = entity.isObfuscationEnabled,
                obfuscationProfileId = entity.obfuscationProfileId,
                autoOpenUrl = entity.autoOpenUrl,
                targetServerId = entity.targetServerId,
                targetServerName = serverName,
                targetCountry = entity.targetCountry,
                targetCity = entity.targetCity,
                localizedCity = localizedCity
            )
        }
    }

    fun connectWithProfile(profile: VpnProfileUiModel) {
        viewModelScope.launch {
            val session = sessionDao.getSession()
            if (session == null) {
                ProtonLogger.e(TAG, "Cannot connect: No session found")
                return@launch
            }

            var servers = vpnRepository.getCachedServers()
            if (servers.isEmpty()) {
                // The cache is empty right after a fresh install or after it was cleared, so
                // refresh it once instead of failing the connection attempt (ANDROID-1ZV).
                ProtonLogger.w(TAG, "Server cache is empty, refreshing before connecting")
                runCatching {
                    vpnRepository.getServers(session.accessToken, session.sessionId, session.userTier)
                }.onFailure { ProtonLogger.w(TAG, "Server refresh failed: ${it.message}") }
                servers = vpnRepository.getCachedServers()
            }
            if (servers.isEmpty()) {
                ProtonLogger.w(TAG, "Cannot connect: Server list is empty")
                return@launch
            }

            val targetServer = findBestServerForProfile(profile, servers)
            if (targetServer == null) {
                // The profile targets a country/city with no server in the current list; the user
                // needs to pick another profile, so this is not a defect to report.
                ProtonLogger.w(TAG, "Cannot connect: No suitable server found for profile")
                return@launch
            }

            // Reliable server selection: Fallback to any server with min load if status == 1 is absent.
            val physicalServer = targetServer.servers.filter { it.status == 1 }.minByOrNull { it.load }
                ?: targetServer.servers.minByOrNull { it.load }

            if (physicalServer == null) {
                ProtonLogger.e(TAG, "Cannot connect: Selected server is currently unavailable.")
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

            if (tunnelState == VpnTunnelState.UP || isConnecting) {
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
                amneziaVpnManager.awaitTunnelAndOpenUrl(profile.autoOpenUrl)
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

    suspend fun getCitiesForCountry(countryCode: String): List<CityDisplayItem> {
        return vpnRepository.getCachedServers()
            .filter { it.exitCountry == countryCode }
            .groupBy { it.city }
            .map { (cityName, cityServers) ->
                val avgLoad = if (cityServers.isEmpty()) 0 else cityServers.map { it.averageLoad }.average().toInt()
                val localizedName = cityServers.firstOrNull()?.localizedCity ?: cityName
                CityDisplayItem(name = cityName, localizedName = localizedName, averageLoad = avgLoad)
            }
            .sortedBy { it.localizedName }
    }

    suspend fun getServersForCity(countryCode: String, city: String): List<LogicalServer> {
        return vpnRepository.getCachedServers()
            .filter { it.exitCountry == countryCode && it.city == city }
            .sortedBy { it.name }
    }

    val customObfuscationConfigs: StateFlow<List<ObfuscationProfile>> = settingsManager.customProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val serverLoadDisplayMode: StateFlow<ServerLoadDisplayMode> = settingsManager.serverLoadDisplayMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ServerLoadDisplayMode.ALL
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
