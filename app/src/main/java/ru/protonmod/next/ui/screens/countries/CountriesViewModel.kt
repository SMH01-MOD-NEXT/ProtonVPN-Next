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

package ru.protonmod.next.ui.screens.countries

import ru.protonmod.next.utils.ProtonLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.protonmod.next.vpn.VpnTunnelState
import ru.protonmod.next.R
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

data class CountryDisplayItem(val code: String, val averageLoad: Int)
data class CityDisplayItem(val name: String, val localizedName: String, val averageLoad: Int)

enum class CountryConnectionMode { STANDARD, MULTI_HOP, TOR }

sealed class BottomSheetContent {
    data class Cities(
        val countryCode: String,
        val cities: List<CityDisplayItem>
    ) : BottomSheetContent()

    data class Servers(
        val countryCode: String,
        val cityName: String,
        val localizedCityName: String,
        val servers: List<LogicalServer>
    ) : BottomSheetContent()
}

sealed class CountriesUiState {
    data object Loading : CountriesUiState()
    data class Success(
        val countries: List<CountryDisplayItem>,
        val bottomSheetContent: BottomSheetContent? = null,
        val loadDisplayMode: ServerLoadDisplayMode = ServerLoadDisplayMode.ALL,
        val isBottomSheetOpen: Boolean = false,
        val connectionMode: CountryConnectionMode = CountryConnectionMode.STANDARD,
        val multiHopEntry: LogicalServer? = null
    ) : CountriesUiState()
    data class Error(val message: String) : CountriesUiState()
}

sealed class NavigationState {
    data object Countries : NavigationState()
    data class Cities(val countryCode: String) : NavigationState()
    data class Servers(val countryCode: String, val cityName: String) : NavigationState()
}

@HiltViewModel
class CountriesViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val connectedServerState: ConnectedServerState,
    private val settingsManager: SettingsManager
) : ViewModel() {

    companion object {
        private const val TAG = "CountriesViewModel"
    }

    private val _navState = MutableStateFlow<NavigationState>(NavigationState.Countries)
    private val _error = MutableStateFlow<String?>(null)
    private val _connectionMode = MutableStateFlow(CountryConnectionMode.STANDARD)
    private val _multiHopEntry = MutableStateFlow<LogicalServer?>(null)

    // Memoized countries list to prevent unnecessary instance changes and recalculations.
    // Use distinctUntilChanged to only emit when the content actually changes (including loads).
    private val _countries = vpnRepository.getServersFlow()
        .map { servers ->
            servers.groupBy { it.exitCountry }
                .map { (code, countryServers) ->
                    val avg = if (countryServers.isEmpty()) 0 else countryServers.map { it.averageLoad }.average().toInt()
                    CountryDisplayItem(code, avg)
                }
                .sortedBy { it.code }
        }
        .distinctUntilChanged()

    val uiState: StateFlow<CountriesUiState> = combine(
        combine(_countries, vpnRepository.getServersFlow(), _navState) { c, s, n -> Triple(c, s, n) },
        vpnRepository.isUpdating,
        combine(settingsManager.serverLoadDisplayMode, _error, _connectionMode, _multiHopEntry) { load, error, mode, entry ->
            arrayOf(load, error, mode, entry)
        }
    ) { (countries, servers, nav), isUpdating, modeState ->
        val loadMode = modeState[0] as ServerLoadDisplayMode
        val error = modeState[1] as String?
        val connectionMode = modeState[2] as CountryConnectionMode
        val multiHopEntry = modeState[3] as LogicalServer?
        if (isUpdating && servers.isEmpty()) {
            return@combine CountriesUiState.Loading
        }
        if (error != null && servers.isEmpty()) {
            return@combine CountriesUiState.Error(error)
        }

        val bottomSheetContent = when (nav) {
            is NavigationState.Countries -> null
            is NavigationState.Cities -> {
                val cities = servers.filter { it.exitCountry == nav.countryCode }
                    .groupBy { it.city }
                    .map { (name, cityServers) ->
                        val avg = if (cityServers.isEmpty()) 0 else cityServers.map { it.averageLoad }.average().toInt()
                        val localizedName = cityServers.firstOrNull()?.localizedCity ?: name
                        CityDisplayItem(name, localizedName, avg)
                    }
                    .sortedBy { it.localizedName }
                BottomSheetContent.Cities(nav.countryCode, cities)
            }
            is NavigationState.Servers -> {
                val cityServers = servers.filter { it.exitCountry == nav.countryCode && it.city == nav.cityName }
                    .sortedBy { it.name }
                val localizedCityName = cityServers.firstOrNull()?.localizedCity ?: nav.cityName
                BottomSheetContent.Servers(nav.countryCode, nav.cityName, localizedCityName, cityServers)
            }
        }

        CountriesUiState.Success(
            countries, bottomSheetContent, loadMode, nav != NavigationState.Countries,
            connectionMode, multiHopEntry
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CountriesUiState.Loading)

    val connectedServer: StateFlow<LogicalServer?> = connectedServerState.connectedServer

    init {
        initialFetch()
        viewModelScope.launch {
            settingsManager.torModeEnabled.collect { enabled ->
                if (enabled) {
                    _multiHopEntry.value = null
                    _connectionMode.value = CountryConnectionMode.TOR
                } else if (_connectionMode.value == CountryConnectionMode.TOR) {
                    _connectionMode.value = CountryConnectionMode.STANDARD
                }
            }
        }
    }

    private fun initialFetch() {
        viewModelScope.launch {
            _error.value = null
            val session = sessionDao.getSession()
            if (session == null) {
                _error.value = context.getString(R.string.error_session_not_found)
                return@launch
            }
            vpnRepository.getServers(session.accessToken, session.sessionId, session.userTier, forceRefresh = false)
                .onFailure { _error.value = it.localizedMessage }
        }
    }

    fun loadServers() {
        initialFetch()
    }

    private fun bestPhysical(server: LogicalServer) =
        server.servers.filter { it.status == 1 }.minByOrNull { it.load }
            ?: server.servers.minByOrNull { it.load }

    private suspend fun connectToServer(server: LogicalServer, entryServer: LogicalServer? = null) {
        // Reliable server selection: Fallback to any server with min load if status == 1 is absent.
        val physicalServer = bestPhysical(server)
        val entryPhysicalServer = entryServer?.let(::bestPhysical)

        if (physicalServer != null && (entryServer == null || entryPhysicalServer != null)) {
            connectedServerState.setConnectedServer(server)
            val tunnelState = amneziaVpnManager.tunnelState.value
            val isConnecting = amneziaVpnManager.isConnecting.value
            // Re-fetch the session right before use so we always pass the freshest credentials,
            // even if a token refresh occurred between server selection and the actual connect call.
            val session = sessionDao.getSession()
            if (session == null) {
                ProtonLogger.e(TAG, "Cannot connect: No session found")
                return
            }
            if (tunnelState == VpnTunnelState.UP || isConnecting) {
                amneziaVpnManager.reconnect(
                    server.id, physicalServer, session,
                    logicalServer = server,
                    multiHopEntryServer = entryPhysicalServer
                )
            } else {
                amneziaVpnManager.connect(
                    server.id, physicalServer, session,
                    logicalServer = server,
                    multiHopEntryServer = entryPhysicalServer
                )
            }
        } else {
            _error.value = context.getString(R.string.label_server_unavailable)
        }
    }

    fun selectCountry(country: String) {
        viewModelScope.launch {
            val servers = vpnRepository.getCachedServers()
            val serversInCountry = servers.filter { it.exitCountry == country }
            if (serversInCountry.isNotEmpty()) {
                val bestServer = serversInCountry
                    .filter { it.servers.any { s -> s.status == 1 } }
                    .minByOrNull { it.averageLoad } 
                    ?: serversInCountry.minByOrNull { it.averageLoad }
                
                bestServer?.let { selectForCurrentMode(it) }
            }
        }
    }

    fun expandCitiesForCountry(country: String) {
        _navState.value = NavigationState.Cities(country)
    }

    fun backToCountries() {
        _navState.value = NavigationState.Countries
    }

    fun selectCity(city: String) {
        viewModelScope.launch {
            val nav = _navState.value
            if (nav !is NavigationState.Cities) return@launch

            val servers = vpnRepository.getCachedServers()
            val serversInCity = servers.filter { it.exitCountry == nav.countryCode && it.city == city }
            if (serversInCity.isNotEmpty()) {
                val bestServer = serversInCity
                    .filter { it.servers.any { s -> s.status == 1 } }
                    .minByOrNull { it.averageLoad }
                    ?: serversInCity.minByOrNull { it.averageLoad }
                    
                bestServer?.let { selectForCurrentMode(it) }
            }
        }
    }

    fun expandServersForCity(city: String) {
        val nav = _navState.value
        if (nav is NavigationState.Cities) {
            _navState.value = NavigationState.Servers(nav.countryCode, city)
        }
    }

    fun backToCities() {
        val nav = _navState.value
        if (nav is NavigationState.Servers) {
            _navState.value = NavigationState.Cities(nav.countryCode)
        }
    }

    fun selectServer(server: LogicalServer) {
        viewModelScope.launch { selectForCurrentMode(server) }
    }

    private suspend fun selectForCurrentMode(server: LogicalServer) {
        if (_connectionMode.value != CountryConnectionMode.MULTI_HOP) {
            connectToServer(server)
            return
        }
        val entry = _multiHopEntry.value
        if (entry == null) {
            _multiHopEntry.value = server
            _navState.value = NavigationState.Countries
        } else if (entry.id != server.id) {
            connectToServer(server, entry)
        } else {
            _error.value = context.getString(R.string.multi_hop_same_server_error)
        }
    }

    fun setConnectionMode(mode: CountryConnectionMode) {
        viewModelScope.launch {
            when (mode) {
                CountryConnectionMode.TOR -> {
                    _multiHopEntry.value = null
                    settingsManager.setTorModeEnabled(true)
                }
                CountryConnectionMode.MULTI_HOP -> settingsManager.setTorModeEnabled(false)
                CountryConnectionMode.STANDARD -> {
                    _multiHopEntry.value = null
                    settingsManager.setTorModeEnabled(false)
                }
            }
            _connectionMode.value = mode
        }
    }
}
