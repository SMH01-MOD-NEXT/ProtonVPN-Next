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
    // Use data object for better toString representation and consistency
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

    private val _servers = MutableStateFlow<List<LogicalServer>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Combine flows cleanly without using Array<Any?> and unsafe casts
    // Nesting `combine` allows us to bypass the 5-argument limit gracefully.
    val uiState: StateFlow<DashboardUiState> = combine(
        combine(_servers, _isLoading, _errorMessage) { servers, isLoading, error ->
            Triple(servers, isLoading, error)
        },
        amneziaVpnManager.tunnelState,
        connectedServerState.connectedServer,
        recentConnectionDao.getRecentConnections()
    ) { localState, tunnelState, connectedServer, recentEntities ->
        val (servers, isLoading, error) = localState

        if (isLoading && servers.isEmpty()) {
            DashboardUiState.Loading
        } else if (error != null && servers.isEmpty()) {
            DashboardUiState.Error(error)
        } else {
            val isConnected = tunnelState == Tunnel.State.UP
            val isConnecting = tunnelState == Tunnel.State.TOGGLE

            // Map recent connections entities back to domain models
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

        // Listen to AmneziaWG tunnel state changes to save history and refresh servers
        viewModelScope.launch {
            amneziaVpnManager.tunnelState.collect { state ->
                if (state == Tunnel.State.UP) {
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
                    refreshServersIfExpired()
                } else if (state == Tunnel.State.DOWN) {
                    connectedServerState.setConnectedServer(null)
                }
            }
        }
    }

    fun loadServers() {
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
                .onSuccess {
                    _servers.value = serversCacheManager.getCachedServers()
                }
                .onFailure { error ->
                    val cachedServers = serversCacheManager.getCachedServers()
                    if (cachedServers.isNotEmpty()) {
                        // Fallback to cached servers if network fails
                        _servers.value = cachedServers
                    } else {
                        _errorMessage.value = error.localizedMessage ?: "Unknown error occurred"
                    }
                }
            _isLoading.value = false
        }
    }

    /**
     * Refresh the servers cache upon VPN connection if it's older than 1 hour.
     */
    private fun refreshServersIfExpired() {
        viewModelScope.launch {
            if (serversCacheManager.isCacheExpired()) {
                val session = sessionDao.getSession()
                if (session != null) {
                    serversCacheManager.getServers(
                        session.accessToken,
                        session.sessionId,
                        session.userTier,
                        forceRefresh = true
                    ).onSuccess {
                        _servers.value = serversCacheManager.getCachedServers()
                    }
                }
            }
        }
    }

    fun toggleConnection(server: LogicalServer) {
        val currentState = uiState.value
        if (currentState is DashboardUiState.Success) {
            viewModelScope.launch {
                if (currentState.isConnected || currentState.isConnecting) {
                    amneziaVpnManager.disconnect()
                } else {
                    connectedServerState.setConnectedServer(server)

                    val session = sessionDao.getSession()
                    if (session == null) {
                        connectedServerState.setConnectedServer(null)
                        return@launch
                    }

                    // Pick the first active physical server inside the logical group
                    val physicalServer = server.servers.firstOrNull { it.status == 1 }
                    if (physicalServer != null) {
                        // Pass logical server ID as the first parameter
                        val result = amneziaVpnManager.connect(server.id, physicalServer, session)

                        // Handle potential connection failure to avoid infinite loading state
                        if (result.isFailure) {
                            connectedServerState.setConnectedServer(null)
                            // State will naturally revert to disconnected
                        }
                    } else {
                        // Revert state if no active physical server is available
                        connectedServerState.setConnectedServer(null)
                        _errorMessage.value = "Selected server is currently unavailable."
                    }
                }
            }
        }
    }
}
