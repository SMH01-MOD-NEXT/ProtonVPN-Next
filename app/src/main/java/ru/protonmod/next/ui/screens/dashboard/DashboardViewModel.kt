package ru.protonmod.next.ui.screens.dashboard

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.Tunnel
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.vpn.AmneziaVpnManager
import javax.inject.Inject

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(
        val servers: List<LogicalServer>,
        val isConnected: Boolean = false,
        val connectedServer: LogicalServer? = null,
        val isConnecting: Boolean = false
    ) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val amneziaVpnManager: AmneziaVpnManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadServers()

        // Listen to AmneziaWG tunnel state changes
        viewModelScope.launch {
            amneziaVpnManager.tunnelState.collect { state ->
                val current = _uiState.value
                if (current is DashboardUiState.Success) {
                    when (state) {
                        Tunnel.State.UP -> _uiState.value = current.copy(isConnected = true, isConnecting = false)
                        Tunnel.State.DOWN -> _uiState.value = current.copy(isConnected = false, isConnecting = false, connectedServer = null)
                        Tunnel.State.TOGGLE -> _uiState.value = current.copy(isConnecting = true)
                    }
                }
            }
        }
    }

    fun loadServers() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            val session = sessionDao.getSession()
            if (session == null) {
                _uiState.value = DashboardUiState.Error("Активная сессия не найдена. Пожалуйста, авторизуйтесь снова.")
                return@launch
            }

            vpnRepository.getServers(session.accessToken, session.sessionId)
                .onSuccess { servers ->
                    _uiState.value = DashboardUiState.Success(servers = servers)
                }
                .onFailure { error ->
                    _uiState.value = DashboardUiState.Error(error.localizedMessage ?: "Неизвестная ошибка")
                }
        }
    }

    fun toggleConnection(server: LogicalServer) {
        val currentState = _uiState.value
        if (currentState is DashboardUiState.Success) {
            viewModelScope.launch {
                if (currentState.isConnected || currentState.isConnecting) {
                    amneziaVpnManager.disconnect()
                } else {
                    _uiState.value = currentState.copy(isConnecting = true, connectedServer = server)

                    val session = sessionDao.getSession()
                    if (session == null) {
                        // Reset state if no session found
                        _uiState.value = currentState.copy(isConnecting = false, connectedServer = null)
                        return@launch
                    }

                    // We pick the first active physical server inside the logical group
                    val physicalServer = server.servers.firstOrNull { it.status == 1 }
                    if (physicalServer != null) {
                        // Pass logical server ID as the first parameter
                        val result = amneziaVpnManager.connect(server.id, physicalServer, session)

                        // Handle potential connection failure to avoid infinite loading state
                        if (result.isFailure) {
                            _uiState.value = currentState.copy(isConnecting = false, connectedServer = null)
                            // Optional: You can emit an error side-effect here to show a Toast or Snackbar
                        }
                    } else {
                        // Revert state if no active physical server is available
                        _uiState.value = currentState.copy(isConnecting = false, connectedServer = null)
                    }
                }
            }
        }
    }
}