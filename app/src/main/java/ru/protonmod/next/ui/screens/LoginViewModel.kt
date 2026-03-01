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
 * Custom exception to trigger the Captcha WebView in the UI.
 * Used when the server returns error code 9001.
 */
class CaptchaRequiredException(val webUrl: String) : Exception("Human Verification Required")

// --- UI State ---

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()

    /**
     * State triggered when Proton requires a Captcha.
     * Stores credentials to resume the login process automatically after success.
     */
    data class RequiresCaptcha(
        val webUrl: String,
        val username: String,
        val passwordRaw: String
    ) : LoginUiState()

    /**
     * State triggered when Two-Factor Authentication is active.
     * Contains session tokens required for the second stage of authentication.
     */
    data class Requires2FA(
        val sessionId: String,
        val tempAccessToken: String,
        val refreshToken: String
    ) : LoginUiState()

    /**
     * Final successful state with permanent tokens and user information.
     */
    data class Success(
        val accessToken: String,
        val userId: String
    ) : LoginUiState()

    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Starts the authentication flow using SRP.
     * If a [captchaToken] is provided, it's sent as a verification header.
     */
    fun login(username: String, passwordRaw: String, captchaToken: String? = null) {
        if (username.isBlank() || passwordRaw.isBlank()) return

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.login(username, passwordRaw, captchaToken)
                .onSuccess { response ->
                    val scopes = response.scopes

                    // Check if 2FA is required based on the scopes returned by the server
                    if (scopes.contains("twofactor")) {
                        _uiState.value = LoginUiState.Requires2FA(
                            sessionId = response.sessionId ?: "",
                            tempAccessToken = response.accessToken ?: "",
                            refreshToken = response.refreshToken ?: ""
                        )
                    } else {
                        // Direct success if 2FA is not enabled for this account
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
     * Submits the TOTP code for accounts with 2FA enabled.
     * Performs a full cycle: verification -> session upgrade -> user profile fetch.
     */
    fun submit2FA(sessionId: String, tempAccessToken: String, refreshToken: String, totpCode: String) {
        if (totpCode.isBlank()) return

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.verify2FA(
                sessionId = sessionId,
                tempAccessToken = tempAccessToken,
                refreshToken = refreshToken,
                totpCode = totpCode
            )
                .onSuccess { response ->
                    // Full login success with valid long-lived AccessToken and actual UserID
                    _uiState.value = LoginUiState.Success(
                        accessToken = response.accessToken ?: "",
                        userId = response.userId ?: ""
                    )
                }
                .onFailure { exception ->
                    _uiState.value = LoginUiState.Error(
                        exception.localizedMessage ?: "Two-factor verification failed"
                    )
                }
        }
    }

    /**
     * Handles various error scenarios, specifically filtering for Captcha requirements.
     */
    private fun handleFailure(throwable: Throwable, username: String, passwordRaw: String) {
        if (throwable is CaptchaRequiredException) {
            _uiState.value = LoginUiState.RequiresCaptcha(throwable.webUrl, username, passwordRaw)
        } else {
            _uiState.value = LoginUiState.Error(
                throwable.localizedMessage ?: "An unexpected authentication error occurred"
            )
        }
    }

    /**
     * Specifically resets only the error state.
     */
    fun resetError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }
}
