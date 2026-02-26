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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.cache.ServersCacheManager
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

sealed class CountriesUiState {
    object Loading : CountriesUiState()
    data class CountriesList(val countries: List<String>) : CountriesUiState()
    data class CitiesList(
        val country: String,
        val cities: List<String>
    ) : CountriesUiState()
    data class ServersList(
        val country: String,
        val city: String,
        val servers: List<LogicalServer>
    ) : CountriesUiState()
    data class Error(val message: String) : CountriesUiState()
}

@HiltViewModel
class CountriesViewModel @Inject constructor(
    private val serversCacheManager: ServersCacheManager,
    private val sessionDao: SessionDao,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val connectedServerState: ConnectedServerState
) : ViewModel() {

    private val _uiState = MutableStateFlow<CountriesUiState>(CountriesUiState.Loading)
    val uiState: StateFlow<CountriesUiState> = _uiState.asStateFlow()

    val connectedServer: StateFlow<LogicalServer?> = connectedServerState.connectedServer

    private var allServers: List<LogicalServer> = emptyList()
    private var serversGroupedByCountry: Map<String, List<LogicalServer>> = emptyMap()
    private var serversGroupedByCityInCountry: Map<String, Map<String, List<LogicalServer>>> = emptyMap()

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _uiState.value = CountriesUiState.Loading
            val session = sessionDao.getSession()
            if (session == null) {
                _uiState.value = CountriesUiState.Error("Активная сессия не найдена.")
                return@launch
            }

            try {
                serversCacheManager.getServers(session.accessToken, session.sessionId)
                allServers = serversCacheManager.getCachedServers()

                if (allServers.isEmpty()) {
                    _uiState.value = CountriesUiState.Error("Серверы не загружены")
                    return@launch
                }

                serversGroupedByCountry = allServers
                    .groupBy { it.exitCountry }
                    .toSortedMap()

                serversGroupedByCityInCountry = serversGroupedByCountry.mapValues { (_, serversByCountry) ->
                    serversByCountry
                        .groupBy { it.city }
                        .toSortedMap()
                }

                val countries = serversGroupedByCountry.keys.toList()
                _uiState.value = CountriesUiState.CountriesList(countries)
            } catch (e: Exception) {
                _uiState.value = CountriesUiState.Error(e.localizedMessage ?: "Неизвестная ошибка")
            }
        }
    }

    private suspend fun connectToServer(server: LogicalServer) {
        val session = sessionDao.getSession()
        if (session == null) {
            return
        }

        // Check if already connecting or connected
        val tunnelState = amneziaVpnManager.tunnelState.value
        if (tunnelState == Tunnel.State.TOGGLE || (tunnelState == Tunnel.State.UP && connectedServerState.connectedServer.value?.id == server.id)) {
            return
        }

        // We pick the first active physical server inside the logical group
        val physicalServer = server.servers.firstOrNull { it.status == 1 }
        if (physicalServer != null) {
            // Set shared state before connecting to show loading in UI
            connectedServerState.setConnectedServer(server)
            
            val result = amneziaVpnManager.connect(server.id, physicalServer, session)
            
            if (result.isFailure) {
                connectedServerState.setConnectedServer(null)
            }
        }
    }

    fun selectCountry(country: String) {
        viewModelScope.launch {
            val serversInCountry = serversGroupedByCountry[country] ?: emptyList()
            if (serversInCountry.isNotEmpty()) {
                val randomServer = serversInCountry.random()
                connectToServer(randomServer)
            }
        }
    }

    fun expandCitiesForCountry(country: String) {
        viewModelScope.launch {
            val citiesInCountry = serversGroupedByCityInCountry[country]?.keys?.toList() ?: emptyList()
            if (citiesInCountry.isNotEmpty()) {
                _uiState.value = CountriesUiState.CitiesList(country, citiesInCountry)
            }
        }
    }

    fun backToCountries() {
        val countries = serversGroupedByCountry.keys.toList()
        _uiState.value = CountriesUiState.CountriesList(countries)
    }

    fun selectCity(city: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is CountriesUiState.CitiesList) return@launch

            val country = currentState.country
            val serversInCity = serversGroupedByCityInCountry[country]?.get(city) ?: emptyList()
            if (serversInCity.isNotEmpty()) {
                val randomServer = serversInCity.random()
                connectToServer(randomServer)
            }
        }
    }

    fun expandServersForCity(city: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is CountriesUiState.CitiesList) return@launch

            val country = currentState.country
            val serversInCity = serversGroupedByCityInCountry[country]?.get(city) ?: emptyList()
            if (serversInCity.isNotEmpty()) {
                _uiState.value = CountriesUiState.ServersList(country, city, serversInCity)
            }
        }
    }

    fun backToCities() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is CountriesUiState.ServersList) return@launch

            val citiesInCountry = serversGroupedByCityInCountry[currentState.country]?.keys?.toList() ?: emptyList()
            _uiState.value = CountriesUiState.CitiesList(currentState.country, citiesInCountry)
        }
    }

    fun selectServer(server: LogicalServer) {
        viewModelScope.launch {
            connectToServer(server)
        }
    }
}
