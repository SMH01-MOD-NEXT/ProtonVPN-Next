package ru.protonmod.next.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.protonmod.next.data.repository.AuthRepository
import javax.inject.Inject

// --- API Error Models ---
@Serializable
data class ProtonErrorResponse(
    @SerialName("Code") val code: Int,
    @SerialName("Details") val details: ProtonErrorDetails? = null
)

@Serializable
data class ProtonErrorDetails(
    @SerialName("WebUrl") val webUrl: String? = null
)

/**
 * Custom exception to trigger the Captcha WebView in the UI
 */
class CaptchaRequiredException(val webUrl: String) : Exception("Human Verification Required")

// --- UI State ---
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()

    // Holds credentials and webUrl to resume login after solving captcha
    data class RequiresCaptcha(val webUrl: String, val username: String, val passwordRaw: String) : LoginUiState()

    // Now holds refreshToken to perform the session upgrade after 2FA
    data class Requires2FA(
        val sessionId: String,
        val tempAccessToken: String,
        val refreshToken: String
    ) : LoginUiState()

    data class Success(val accessToken: String, val userId: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Initial login attempt using SRP.
     * Can be called with a captchaToken if redirected from the WebView.
     */
    fun login(username: String, passwordRaw: String, captchaToken: String? = null) {
        if (username.isBlank() || passwordRaw.isBlank()) return

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.login(username, passwordRaw, captchaToken)
                .onSuccess { response ->
                    if (response.scopes.contains("twofactor")) {
                        // Store the session UID, the restricted token, and the refresh token.
                        _uiState.value = LoginUiState.Requires2FA(
                            sessionId = response.sessionId ?: "",
                            tempAccessToken = response.accessToken ?: "",
                            refreshToken = response.refreshToken ?: ""
                        )
                    } else {
                        // Fully logged in directly
                        _uiState.value = LoginUiState.Success(
                            accessToken = response.accessToken ?: "",
                            userId = response.userId ?: ""
                        )
                    }
                }
                .onFailure { exception ->
                    handleFailure(exception, username, passwordRaw)
                }
        }
    }

    /**
     * Final step of 2FA login.
     * Verifies code, refreshes session for full tokens, and fetches User ID.
     */
    fun submit2FA(sessionId: String, tempAccessToken: String, refreshToken: String, totpCode: String) {
        if (totpCode.isBlank()) return

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.verify2FA(sessionId, tempAccessToken, refreshToken, totpCode)
                .onSuccess { response ->
                    // Full login success with valid UserID
                    _uiState.value = LoginUiState.Success(
                        accessToken = response.accessToken ?: "",
                        userId = response.userId ?: ""
                    )
                }
                .onFailure { exception ->
                    _uiState.value = LoginUiState.Error(exception.localizedMessage ?: "2FA verification failed")
                }
        }
    }

    private fun handleFailure(throwable: Throwable, username: String, passwordRaw: String) {
        if (throwable is CaptchaRequiredException) {
            _uiState.value = LoginUiState.RequiresCaptcha(throwable.webUrl, username, passwordRaw)
        } else {
            _uiState.value = LoginUiState.Error(throwable.localizedMessage ?: "An unexpected error occurred")
        }
    }

    fun resetError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }
}