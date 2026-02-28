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

package ru.protonmod.next.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.cache.ServersCacheManager
import ru.protonmod.next.data.local.RecentConnectionEntity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(
        val servers: List<LogicalServer>,
        val recentConnections: List<LogicalServer> = emptyList(),
        val isConnected: Boolean = false,
        val connectedServer: LogicalServer? = null,
        val isConnecting: Boolean = false
    ) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val serversCacheManager: ServersCacheManager,
    private val sessionDao: SessionDao,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val connectedServerState: ConnectedServerState,
    private val recentConnectionDao: ru.protonmod.next.data.local.RecentConnectionDao
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(serversCacheManager.getServersFlow(), _isLoading, _errorMessage) { servers, isLoading, error ->
            Triple(servers, isLoading, error)
        },
        amneziaVpnManager.tunnelState,
        amneziaVpnManager.isConnecting,
        connectedServerState.connectedServer,
        recentConnectionDao.getRecentConnections()
    ) { localState, tunnelState, isConnecting, connectedServer, recentEntities ->
        val (servers, isLoading, error) = localState

        if (isLoading && servers.isEmpty()) {
            DashboardUiState.Loading
        } else if (error != null && servers.isEmpty()) {
            DashboardUiState.Error(error)
        } else {
            val isConnected = tunnelState == Tunnel.State.UP

            val recentServers = recentEntities.mapNotNull { entity ->
                servers.find { it.id == entity.serverId }
            }

            DashboardUiState.Success(
                servers = servers,
                recentConnections = recentServers,
                isConnected = isConnected,
                connectedServer = connectedServer,
                isConnecting = isConnecting
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState.Loading)

    init {
        loadServers()

        viewModelScope.launch {
            amneziaVpnManager.tunnelState.collect { state ->
                if (state == Tunnel.State.UP) {
                    // Give the tunnel 3 seconds to stabilize routing before updating servers/load
                    delay(3000)
                    
                    connectedServerState.connectedServer.value?.let { server ->
                        recentConnectionDao.addRecentConnection(
                            RecentConnectionEntity(
                                serverId = server.id,
                                serverName = server.name,
                                city = server.city,
                                country = server.exitCountry,
                                lastConnectedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    // Refresh server loads after connection is established
                    loadServers()
                } else if (state == Tunnel.State.DOWN) {
                    connectedServerState.setConnectedServer(null)
                }
            }
        }
    }

    fun loadServers() {
        if (uiState.value is DashboardUiState.Error) {
            _isLoading.value = true
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val session = sessionDao.getSession()
            if (session == null) {
                _errorMessage.value = "Active session not found. Please log in again."
                _isLoading.value = false
                return@launch
            }

            serversCacheManager.getServers(session.accessToken, session.sessionId, session.userTier)
                .onFailure { error ->
                    val cachedServers = serversCacheManager.getCachedServers()
                    if (cachedServers.isEmpty()) {
                        _errorMessage.value = error.localizedMessage ?: "Unknown error occurred"
                    }
                }
            _isLoading.value = false
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            amneziaVpnManager.disconnect()
        }
    }

    fun toggleConnection(server: LogicalServer) {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState !is DashboardUiState.Success) return@launch

            val isConnectedToAny = currentState.isConnected || currentState.isConnecting
            val isTargetServerConnected = currentState.connectedServer?.id == server.id

            if (isConnectedToAny) {
                if (isTargetServerConnected) {
                    disconnect()
                } else {
                    initiateConnection(server)
                }
            } else {
                initiateConnection(server)
            }
        }
    }

    private suspend fun initiateConnection(server: LogicalServer) {
        val session = sessionDao.getSession()
        if (session == null) {
            _errorMessage.value = "Active session not found. Please log in again."
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
            _errorMessage.value = "Selected server is currently unavailable."
        }
    }
}
