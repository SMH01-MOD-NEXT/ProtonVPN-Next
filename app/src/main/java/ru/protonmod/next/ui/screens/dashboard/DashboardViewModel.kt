package ru.protonmod.next.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val vpnRepository: VpnRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // TODO: В реальном приложении эти токены нужно брать из DataStore / SharedPreferences
    private var tempAccessToken: String = ""
    private var tempSessionId: String = ""

    /**
     * Инициализация с токенами, полученными после логина
     */
    fun loadServers(accessToken: String, sessionId: String) {
        this.tempAccessToken = accessToken
        this.tempSessionId = sessionId

        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            vpnRepository.getServers(accessToken, sessionId)
                .onSuccess { servers ->
                    _uiState.value = DashboardUiState.Success(servers = servers)
                }
                .onFailure { error ->
                    _uiState.value = DashboardUiState.Error(error.localizedMessage ?: "Unknown error")
                }
        }
    }

    fun toggleConnection(server: LogicalServer) {
        val currentState = _uiState.value
        if (currentState is DashboardUiState.Success) {
            val isConnecting = !currentState.isConnected

            // TODO: Здесь будет логика инициализации AmneziaWG туннеля
            // 1. Сгенерировать WG ключи
            // 2. Отправить POST /vpn/v1/certificate
            // 3. Собрать WG Config и запустить туннель

            _uiState.value = currentState.copy(
                isConnected = isConnecting,
                connectedServer = if (isConnecting) server else null
            )
        }
    }
}