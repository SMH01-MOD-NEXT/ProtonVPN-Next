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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.cache.ServersCacheManager
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

data class CountryDisplayItem(val code: String, val averageLoad: Int)
data class CityDisplayItem(val name: String, val averageLoad: Int)

sealed class CountriesUiState {
    data object Loading : CountriesUiState()
    data class CountriesList(val countries: List<CountryDisplayItem>) : CountriesUiState()
    data class CitiesList(
        val country: String,
        val cities: List<CityDisplayItem>
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

    companion object {
        private const val TAG = "CountriesViewModel"
    }

    private val _uiState = MutableStateFlow<CountriesUiState>(CountriesUiState.Loading)
    val uiState: StateFlow<CountriesUiState> = _uiState.asStateFlow()

    val connectedServer: StateFlow<LogicalServer?> = connectedServerState.connectedServer

    private var allServers: List<LogicalServer> = emptyList()
    private var serversGroupedByCountry: Map<String, List<LogicalServer>> = emptyMap()
    private var serversGroupedByCityInCountry: Map<String, Map<String, List<LogicalServer>>> = emptyMap()

    init {
        observeServers()
        initialFetch()
    }

    private fun observeServers() {
        viewModelScope.launch {
            serversCacheManager.getServersFlow().collect { servers ->
                if (servers.isNotEmpty()) {
                    allServers = servers
                    processServers(servers)
                }
            }
        }
    }

    private fun initialFetch() {
        viewModelScope.launch {
            val session = sessionDao.getSession() ?: return@launch
            serversCacheManager.getServers(session.accessToken, session.sessionId, session.userTier, forceRefresh = false)
        }
    }

    private suspend fun processServers(servers: List<LogicalServer>) {
        withContext(Dispatchers.Default) {
            serversGroupedByCountry = servers
                .groupBy { it.exitCountry }
                .toSortedMap()

            serversGroupedByCityInCountry = serversGroupedByCountry.mapValues { (_, serversByCountry) ->
                serversByCountry
                    .groupBy { it.city }
                    .toSortedMap()
            }
        }

        if (_uiState.value is CountriesUiState.Loading || _uiState.value is CountriesUiState.CountriesList) {
            val countries = serversGroupedByCountry.map { (code, servers) ->
                val avg = if (servers.isEmpty()) 0 else servers.map { it.averageLoad }.average().toInt()
                CountryDisplayItem(code, avg)
            }
            _uiState.value = CountriesUiState.CountriesList(countries)
        }
    }

    fun loadServers() {
        if (_uiState.value is CountriesUiState.Error) {
            _uiState.value = CountriesUiState.Loading
            initialFetch()
        }
    }

    private suspend fun connectToServer(server: LogicalServer) {
        val session = sessionDao.getSession()
        if (session == null) {
            Log.e(TAG, "Cannot connect: No session found")
            return
        }

        val physicalServer = server.servers.firstOrNull { it.status == 1 }

        if (physicalServer != null) {
            connectedServerState.setConnectedServer(server)
            val tunnelState = amneziaVpnManager.tunnelState.value
            val isConnecting = amneziaVpnManager.isConnecting.value
            if (tunnelState == Tunnel.State.UP || isConnecting) {
                amneziaVpnManager.reconnect(server.id, physicalServer, session)
            } else {
                amneziaVpnManager.connect(server.id, physicalServer, session)
            }
        } else {
            _uiState.value = CountriesUiState.Error("Selected server is currently unavailable.")
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
            val citiesInCountry = serversGroupedByCityInCountry[country]?.map { (name, servers) ->
                val avg = if (servers.isEmpty()) 0 else servers.map { it.averageLoad }.average().toInt()
                CityDisplayItem(name, avg)
            } ?: emptyList()

            if (citiesInCountry.isNotEmpty()) {
                _uiState.value = CountriesUiState.CitiesList(country, citiesInCountry)
            }
        }
    }

    fun backToCountries() {
        val countries = serversGroupedByCountry.map { (code, servers) ->
            val avg = if (servers.isEmpty()) 0 else servers.map { it.averageLoad }.average().toInt()
            CountryDisplayItem(code, avg)
        }
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

            val citiesInCountry = serversGroupedByCityInCountry[currentState.country]?.map { (name, servers) ->
                val avg = if (servers.isEmpty()) 0 else servers.map { it.averageLoad }.average().toInt()
                CityDisplayItem(name, avg)
            } ?: emptyList()

            _uiState.value = CountriesUiState.CitiesList(currentState.country, citiesInCountry)
        }
    }

    fun selectServer(server: LogicalServer) {
        viewModelScope.launch {
            connectToServer(server)
        }
    }
}