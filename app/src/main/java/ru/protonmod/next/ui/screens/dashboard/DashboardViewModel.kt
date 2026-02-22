package ru.protonmod.next.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.repository.VpnRepository
import javax.inject.Inject

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(
        val servers: List<LogicalServer>,
        val isConnected: Boolean = false,
        val connectedServer: LogicalServer? = null
    ) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao // Injecting Room DB DAO
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * Loads servers using the session stored in Room Database.
     * Takes no parameters since it resolves tokens internally.
     */
    fun loadServers() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading

            // 1. Get session from local DB
            val session = sessionDao.getSession()
            if (session == null) {
                _uiState.value = DashboardUiState.Error("Активная сессия не найдена. Пожалуйста, авторизуйтесь снова.")
                return@launch
            }

            // 2. Fetch servers using tokens from DB
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
            val isConnecting = !currentState.isConnected

            // TODO: Here we will exchange WG keys using POST /vpn/v1/certificate
            // and pass the configuration to the AmneziaWG backend

            _uiState.value = currentState.copy(
                isConnected = isConnecting,
                connectedServer = if (isConnecting) server else null
            )
        }
    }
}