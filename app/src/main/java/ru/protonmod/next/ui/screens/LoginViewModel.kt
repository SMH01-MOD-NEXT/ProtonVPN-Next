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
import ru.protonmod.next.utils.ProtonLogger
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.net.SocketTimeoutException
import java.net.ConnectException
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SetupStep
import ru.protonmod.next.data.repository.AuthRepository
import ru.protonmod.next.data.repository.ProtonApiException
import ru.protonmod.next.data.network.byedpi.ByeDpiStrategyTester
import ru.protonmod.next.data.network.byedpi.ByeDpiManager
import ru.protonmod.next.utils.NetworkMonitor
import ru.protonmod.next.utils.RegionUtils
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

// --- API Error Models ---

@Serializable
data class ProtonErrorResponse(
    @SerialName("Code") val code: Int,
    @SerialName("Details") val details: ProtonErrorDetails? = null
)

@Serializable
data class ProtonErrorDetails(
    @SerialName("WebUrl") val webUrl: String? = null,
    @SerialName("HumanVerificationToken") val humanVerificationToken: String? = null
)

class CaptchaRequiredException(val webUrl: String, val token: String, val sessionId: String?) : Exception("Human Verification Required")

// --- UI State ---

data class InitialTelemetrySettings(
    val crashReports: Boolean = true,
    val nonFatalErrors: Boolean = true,
    val anrDetection: Boolean = false,
    val metrics: Boolean = false,
    val logs: Boolean = false,
    val performance: Boolean = false,
    val analytics: Boolean = false,
    val sessionReplay: Boolean = false
)

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()

    data class RequiresCaptcha(
        val webUrl: String,
        val username: String,
        val passwordRaw: String,
        val captchaToken: String,
        val isAnonymous: Boolean = false,
        val sessionId: String? = null,
        val nonce: Long = System.currentTimeMillis()
    ) : LoginUiState()

    data class Requires2FA(
        val sessionId: String,
        val tempAccessToken: String,
        val refreshToken: String
    ) : LoginUiState()

    data class Success(
        val accessToken: String,
        val userId: String
    ) : LoginUiState()

    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val settingsManager: SettingsManager,
    private val networkMonitor: NetworkMonitor,
    private val byeDpiManager: ByeDpiManager,
    val byeDpiStrategyTester: ByeDpiStrategyTester,
    val okHttpClient: okhttp3.OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _isByeDpiAutoTesting = MutableStateFlow(false)
    val isByeDpiAutoTesting = _isByeDpiAutoTesting.asStateFlow()

    private var hasRunAutoByeDpiTest = false

    val isApiBypassEnabled = settingsManager.apiBypassEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val apiBypassStrategy = settingsManager.apiBypassStrategy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsManager.STRATEGY_PROTON_MIRRORS)

    val setupStep = settingsManager.setupStep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SetupStep.WELCOME)

    // Onboarding temporary states
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    fun setUsername(name: String) {
        _username.value = name
    }

    fun setVpnPort(port: Int) {
        viewModelScope.launch {
            settingsManager.setVpnPort(port)
        }
    }

    fun setObfuscationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setObfuscationEnabled(enabled)
        }
    }

    fun setServerLoadDisplayMode(mode: ru.protonmod.next.data.local.ServerLoadDisplayMode) {
        viewModelScope.launch {
            settingsManager.setServerLoadDisplayMode(mode)
        }
    }

    fun setAppTheme(theme: ru.protonmod.next.ui.theme.AppTheme) {
        viewModelScope.launch {
            settingsManager.setAppTheme(theme)
        }
    }

    fun setTelemetrySettings(settings: InitialTelemetrySettings) {
        viewModelScope.launch {
            settingsManager.setCrashReportsEnabled(settings.crashReports)
            settingsManager.setSentryNonFatalEnabled(settings.nonFatalErrors)
            settingsManager.setSentryAnrEnabled(settings.anrDetection)
            settingsManager.setSentryMetricsEnabled(settings.metrics)
            settingsManager.setSentryLogsEnabled(settings.logs)
            settingsManager.setSentryPerformanceEnabled(settings.performance)
            settingsManager.setAnalyticsEnabled(settings.analytics)
            settingsManager.setSentrySessionReplayEnabled(settings.sessionReplay)
            settingsManager.setSetupStep(SetupStep.COMPLETE)
            ru.protonmod.next.vpn.SentryConfigurator.applySettings(settingsManager)
        }
    }

    fun setSetupStep(step: SetupStep) {
        viewModelScope.launch {
            settingsManager.setSetupStep(step)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ProtonLogger.d("LoginViewModel", "ViewModel cleared, cancelling pending auth operations")
        authRepository.cancelPendingOperations()
    }

    fun login(username: String, passwordRaw: String, captchaToken: String? = null) {
        if (username.isBlank() || passwordRaw.isBlank() || _uiState.value is LoginUiState.Loading) return

        viewModelScope.launch {
            if (shouldRunAutoByeDpiTest()) {
                runAutoByeDpiTest { login(username, passwordRaw, captchaToken) }
                return@launch
            }

            ProtonLogger.action("Login", "User clicked Login")
            val startTime = System.currentTimeMillis()
            _uiState.value = LoginUiState.Loading

            var isSuccessful = false
            try {
                authRepository.login(username, passwordRaw, captchaToken)
                    .onSuccess { response ->
                        ProtonLogger.i("Login", "Login successful")
                        val duration = System.currentTimeMillis() - startTime
                        ProtonLogger.recordDistribution("login_latency", duration.toDouble())
                        ProtonLogger.recordCount("login_success", 1.0)

                        val scopes = response.scopes
                        if (scopes.contains("twofactor")) {
                            ProtonLogger.i("Login", "2FA required")
                            _uiState.value = LoginUiState.Requires2FA(
                                sessionId = response.sessionId ?: "",
                                tempAccessToken = response.accessToken ?: "",
                                refreshToken = response.refreshToken ?: ""
                            )
                        } else {
                            isSuccessful = true
                            viewModelScope.launch {
                                settingsManager.setPolicyAcceptedVersion(SettingsManager.CURRENT_POLICY_VERSION)
                            }
                            _uiState.value = LoginUiState.Success(
                                accessToken = response.accessToken ?: "",
                                userId = response.userId ?: ""
                            )
                        }
                    }
                    .onFailure { exception ->
                        if (exception is CancellationException) return@onFailure

                        ProtonLogger.recordCount("login_error", 1.0)

                        if (exception is CaptchaRequiredException) {
                            ProtonLogger.w("Login", "Captcha required")
                            _uiState.value = LoginUiState.RequiresCaptcha(
                                webUrl = exception.webUrl,
                                username = username,
                                passwordRaw = passwordRaw,
                                captchaToken = exception.token,
                                isAnonymous = false,
                                sessionId = exception.sessionId
                            )
                        } else if (exception is SocketTimeoutException || exception is ConnectException) {
                            ProtonLogger.w("Login", "Network error during login: ${exception.message}")
                            _uiState.value = LoginUiState.Error(
                                "Connection timeout. Please check your internet and try again."
                            )
                        } else if (exception is ProtonApiException) {
                            // The API rejected the attempt (wrong credentials, rate limit, expired
                            // captcha). The user sees why, so it is not a defect worth reporting.
                            ProtonLogger.w("Login", "Login rejected by API (HTTP ${exception.code})")
                            _uiState.value = LoginUiState.Error(
                                exception.localizedMessage ?: "An unexpected authentication error occurred"
                            )
                        } else {
                            ProtonLogger.e("Login", "Login failed: ${exception.message}", exception)
                            _uiState.value = LoginUiState.Error(
                                exception.localizedMessage ?: "An unexpected authentication error occurred"
                            )
                        }
                    }
            } finally {
                val nextState = _uiState.value
                val isPending = nextState is LoginUiState.RequiresCaptcha || nextState is LoginUiState.Requires2FA
            }
        }
    }

    fun loginBySessionJson(json: String) {
        if (json.isBlank()) return

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val session = Json.decodeFromString<SessionEntity>(json)
                authRepository.loginBySession(session)
                    .onSuccess {
                        viewModelScope.launch {
                            settingsManager.setPolicyAcceptedVersion(SettingsManager.CURRENT_POLICY_VERSION)
                        }
                        _uiState.value = LoginUiState.Success(session.accessToken, session.userId)
                    }
                    .onFailure { exception ->
                        _uiState.value = LoginUiState.Error(
                            exception.localizedMessage ?: "Invalid session format"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("Failed to parse session JSON")
            }
        }
    }

    fun submit2FA(sessionId: String, tempAccessToken: String, refreshToken: String, totpCode: String) {
        if (totpCode.isBlank()) return

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            var isSuccessful = false
            try {
                authRepository.verify2FA(sessionId, tempAccessToken, refreshToken, totpCode)
                    .onSuccess { response ->
                        isSuccessful = true
                        viewModelScope.launch {
                            settingsManager.setPolicyAcceptedVersion(SettingsManager.CURRENT_POLICY_VERSION)
                        }
                        _uiState.value = LoginUiState.Success(
                            accessToken = response.accessToken ?: "",
                            userId = response.userId ?: ""
                        )
                    }
                    .onFailure { exception ->
                        if (exception is CancellationException) return@onFailure
                        _uiState.value = LoginUiState.Error(exception.localizedMessage ?: "Two-factor verification failed")
                    }
            } finally {
            }
        }
    }

    fun loginAnonymous(captchaToken: String? = null) {
        if (_uiState.value is LoginUiState.Loading) return

        viewModelScope.launch {
            if (shouldRunAutoByeDpiTest()) {
                runAutoByeDpiTest { loginAnonymous(captchaToken) }
                return@launch
            }

            ProtonLogger.action("Login", "User clicked Login Anonymous")
            var isSuccessful = false
            try {
                _uiState.value = LoginUiState.Loading
                authRepository.loginAnonymous(captchaToken)
                    .onSuccess { response ->
                        ProtonLogger.i("Login", "Anonymous login successful")
                        isSuccessful = true
                        viewModelScope.launch {
                            settingsManager.setPolicyAcceptedVersion(SettingsManager.CURRENT_POLICY_VERSION)
                        }
                        _uiState.value = LoginUiState.Success(
                            accessToken = response.accessToken ?: "",
                            userId = response.userId ?: ""
                        )
                    }
                    .onFailure { exception ->
                        if (exception is CancellationException) return@onFailure

                        if (exception is CaptchaRequiredException) {
                            ProtonLogger.w("Login", "Anonymous login requires captcha")
                            _uiState.value = LoginUiState.RequiresCaptcha(
                                webUrl = exception.webUrl,
                                username = "",
                                passwordRaw = "",
                                captchaToken = exception.token,
                                isAnonymous = true,
                                sessionId = exception.sessionId
                            )
                        } else if (exception is SocketTimeoutException || exception is ConnectException) {
                            ProtonLogger.w("Login", "Network error during anonymous login: ${exception.message}")
                            _uiState.value = LoginUiState.Error(
                                "Connection timeout. Please check your internet and try again."
                            )
                        } else if (exception is ProtonApiException) {
                            ProtonLogger.w("Login", "Anonymous login rejected by API (HTTP ${exception.code})")
                            _uiState.value = LoginUiState.Error(exception.localizedMessage ?: "Guest login failed")
                        } else {
                            ProtonLogger.e("Login", "Anonymous login failed: ${exception.message}", exception)
                            _uiState.value = LoginUiState.Error(exception.localizedMessage ?: "Guest login failed")
                        }
                    }
            } finally {
                val nextState = _uiState.value
                val isPending = nextState is LoginUiState.RequiresCaptcha
            }
        }
    }

    private fun shouldRunAutoByeDpiTest(): Boolean {
        return RegionUtils.isRussianTimezone() && 
               !networkMonitor.isVpnActive.value && 
               !hasRunAutoByeDpiTest &&
               !settingsManager.isApiBypassEnabledSync()
    }

    private fun runAutoByeDpiTest(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isByeDpiAutoTesting.value = true
            hasRunAutoByeDpiTest = true
            try {
                val sites = try {
                    context.assets.open("proxytest_proton.sites").bufferedReader().readLines().filter { it.isNotBlank() }
                } catch (e: Exception) {
                    listOf("google.com", "proton.me", "github.com")
                }
                
                // Special mode for auto-test: full strategies, success threshold 2
                if (!byeDpiStrategyTester.isTesting.value) {
                    byeDpiStrategyTester.startTesting("full", sites, successThreshold = 2)
                }
                
                // Wait for testing to finish (either success or exhaustion)
                // Use dropWhile to skip the initial 'false' if the test hasn't started yet
                withTimeoutOrNull(300000) { // 5 minutes max
                    byeDpiStrategyTester.isTesting
                        .dropWhile { !it }
                        .first { !it }
                }

                ProtonLogger.i("LoginViewModel", "Auto ByeDPI test finished, waiting for proxy stabilization...")
                
                // Wait for proxy to be running if it was applied
                if (settingsManager.apiBypassStrategy.first() == SettingsManager.STRATEGY_BYEDPI &&
                    settingsManager.apiBypassEnabled.first()) {
                    withTimeoutOrNull(5000) {
                        byeDpiManager.isRunning.first { it }
                    }
                    // Small grace delay for native stability
                    delay(1000)
                }

                ProtonLogger.i("LoginViewModel", "Proxy stable, proceeding to login flow")
                onComplete()
            } catch (e: CancellationException) {
                // The user navigated away or started the login manually; nothing to report.
                throw e
            } catch (e: Exception) {
                ProtonLogger.w("LoginViewModel", "Error during auto ByeDPI test: ${e.message}")
                onComplete()
            } finally {
                _isByeDpiAutoTesting.value = false
            }
        }
    }

    fun stopAutoByeDpiTest() {
        byeDpiStrategyTester.stopTesting()
        _isByeDpiAutoTesting.value = false
    }

    fun retryWithCaptcha(state: LoginUiState.RequiresCaptcha, verifiedToken: String) {
        if (state.isAnonymous) {
            loginAnonymous(verifiedToken)
        } else {
            login(state.username, state.passwordRaw, verifiedToken)
        }
    }

    fun resetError() {
        if (_uiState.value is LoginUiState.RequiresCaptcha) {
            // Only clear auth on dismiss so subsequent retries start completely fresh.
            authRepository.clearPendingAuth()
        }
        if (_uiState.value is LoginUiState.Error || _uiState.value is LoginUiState.RequiresCaptcha) {
            _uiState.value = LoginUiState.Idle
        }
    }

    fun disableBypass() {
        viewModelScope.launch {
            settingsManager.setApiBypassEnabled(false)
        }
    }
}
