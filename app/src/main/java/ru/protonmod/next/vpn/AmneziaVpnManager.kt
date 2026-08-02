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

package ru.protonmod.next.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.net.toUri
import ru.protonmod.next.utils.ProtonLogger
import retrofit2.HttpException
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Inet4Address
import java.net.InetAddress
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.ConnectionVerificationMode
import ru.protonmod.next.netshield.LocalNetShield
import ru.protonmod.next.data.local.SessionEntity
import kotlin.time.Duration.Companion.milliseconds
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.AuthRepository
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.di.ApplicationScope
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import ru.protonmod.next.utils.crypto.CryptoWrapper
import ru.protonmod.next.utils.system.SystemContextWrapper
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class AmneziaVpnManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val vpnRepositoryProvider: Provider<VpnRepository>,
    private val sessionDao: SessionDao,
    private val connectedServerState: ConnectedServerState,
    private val systemContextWrapper: SystemContextWrapper,
    private val cryptoWrapper: CryptoWrapper,
    private val awgBoxConfigGenerator: AwgBoxConfigGenerator,
    private val localNetShield: LocalNetShield,
    private val nextVpnManager: NextVpnManager,
    private val authRepositoryProvider: Provider<AuthRepository>,
    private val vpnNetworkMonitor: VpnNetworkMonitor,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AmneziaVpnManager"
        private const val PROTON_CLIENT_IP = "10.2.0.2"
        private const val PROTON_DNS_IP = "10.2.0.1" // Fallback default DNS
        private const val DNS_RETRY_COUNT = 5
        private const val DNS_RETRY_DELAY_MS = 1000L
        private const val STATE_CONNECTING = "CONNECTING"

        private const val REFRESH_THRESHOLD_MS = 1 * 3600 * 1000L // 1 hour
        private const val RETRY_DELAY_MS = 15 * 60 * 1000L // 15 minutes
        private const val PERIODIC_REFRESH_MS = 2 * 3600 * 1000L // 2 hours
    }

    sealed class CertificateState {
        data object Valid : CertificateState()
        data class ExpiringSoon(val hoursRemaining: Int) : CertificateState()
        data object Expired : CertificateState()
        data class RefreshFailed(val error: String, val isFullyExpired: Boolean) : CertificateState()
        data object Refreshing : CertificateState()
        data class Error(val message: String) : CertificateState()
    }

    private val _certState = MutableStateFlow<CertificateState>(CertificateState.Valid)
    val certState: StateFlow<CertificateState> = _certState.asStateFlow()

    sealed interface ConnectionWarning {
        data object Ipv6OnlyEndpoint : ConnectionWarning
        data object InvalidProxyConfiguration : ConnectionWarning
    }

    private class ExpectedConnectionException(
        val warning: ConnectionWarning,
        message: String
    ) : IllegalArgumentException(message)

    private val _connectionWarning = MutableStateFlow<ConnectionWarning?>(null)
    val connectionWarning: StateFlow<ConnectionWarning?> = _connectionWarning.asStateFlow()

    data class ObfuscationParams(
        val jc: Int, val jmin: Int, val jmax: Int,
        val s1: Int, val s2: Int, val s3: Int = 0, val s4: Int = 0,
        val h1: String, val h2: String, val h3: String, val h4: String,
        val i1: String, val i2: String = "", val i3: String = "", val i4: String = "", val i5: String = "",
        val headerProtectionKey: String = "",
        val contentPaddingAddition: String = "",
        val rekeyAfterTime: String = "",
        val rekeyTimeout: String = "",
        val rejectAfterTime: String = "",
        val keepaliveTimeout: String = "",
        val maxHandshakeAttempts: String = "",
        val persistentKeepalive: String = ""
    )

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    enum class VpnState {
        DISCONNECTED,
        CONNECTING,
        VERIFYING,
        CONNECTED,
        DISCONNECTING
    }

    private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private fun updateVpnState(newState: VpnState) {
        _vpnState.value = newState
        nextVpnManager.setState(newState)
    }

    private val _speed = MutableStateFlow<String?>(null)
    val speed: StateFlow<String?> = _speed.asStateFlow()

    private val _trafficRx = MutableStateFlow<String?>(null)
    val trafficRx: StateFlow<String?> = _trafficRx.asStateFlow()

    private val _trafficTx = MutableStateFlow<String?>(null)
    val trafficTx: StateFlow<String?> = _trafficTx.asStateFlow()

    private val _tunnelState = MutableStateFlow(VpnTunnelState.DOWN)
    val tunnelState: StateFlow<VpnTunnelState> = _tunnelState

    private val _rawTunnelState = MutableStateFlow(VpnTunnelState.DOWN)

    /** Parameters of the last connection attempt, replayed by [reconnectCurrent]. */
    private data class LastConnectionRequest(
        val logicalServerId: String,
        val server: PhysicalServer,
        val overridePort: Int?,
        val overrideObfuscation: Boolean?,
        val obfuscationParams: ObfuscationParams?,
        val multiHopEntryServer: PhysicalServer?
    )

    @Volatile
    private var lastConnectionRequest: LastConnectionRequest? = null

    private var isReconnecting = false
    private var isPaused = false
    private var pauseJob: Job? = null
    private var currentServerId: String? = null
    private var connectionJob: Job? = null
    private var verificationJob: Job? = null
    private var verificationCycle: VpnNetworkMonitor.VerificationCycle? = null
    private var refreshJob: Job? = null
    private val refreshMutex = Mutex()

    init {
        val filter = IntentFilter().apply {
            addAction(ProtonVpnService.ACTION_STATE_CHANGED)
            addAction(ProtonVpnService.ACTION_STATS_UPDATED)
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ProtonVpnService.ACTION_STATE_CHANGED -> {
                        val stateStr = intent.getStringExtra(ProtonVpnService.EXTRA_STATE)
                        val serverId = intent.getStringExtra(ProtonVpnService.EXTRA_LOGICAL_SERVER_ID)
                        val isServiceReconnecting = intent.getBooleanExtra(ProtonVpnService.EXTRA_IS_RECONNECTING, false)
                        
                        if (serverId != null && serverId != currentServerId && stateStr != VpnTunnelState.DOWN.name) {
                            currentServerId = serverId
                            applicationScope.launch {
                                val resolved = vpnRepositoryProvider.get().getCachedServers().find { it.id == serverId }
                                if (resolved != null) {
                                    connectedServerState.setConnectedServer(resolved)
                                }
                            }
                        }

                        stateStr?.let { stateLabel ->
                            if (stateLabel == STATE_CONNECTING) {
                                if (!_isConnecting.value) {
                                    verificationCycle = vpnNetworkMonitor.beginVerificationCycle()
                                }
                                _isConnecting.value = true
                                updateVpnState(VpnState.CONNECTING)
                                return@let
                            }

                            val newState = runCatching { VpnTunnelState.valueOf(stateLabel) }
                                .getOrElse {
                                    ProtonLogger.e(TAG, "Failed to parse tunnel state: $stateLabel")
                                    return@let
                                }
                            val previousState = _rawTunnelState.value
                            val serviceVerified = intent.getBooleanExtra(
                                ProtonVpnService.EXTRA_VERIFIED,
                                false
                            )

                            _rawTunnelState.value = newState
                            _tunnelState.value = newState
                            _isConnecting.value = false

                            when (newState) {
                                VpnTunnelState.UP -> when {
                                    serviceVerified -> {
                                        updateVpnState(VpnState.CONNECTED)
                                        if (previousState != VpnTunnelState.UP) {
                                            startTunnelVerification()
                                        }
                                    }
                                    previousState != VpnTunnelState.UP ||
                                        _vpnState.value == VpnState.CONNECTING -> {
                                        handleTunnelStateChange(VpnTunnelState.UP)
                                    }
                                    else -> ProtonLogger.v(TAG, "Ignoring duplicate tunnel UP state")
                                }
                                VpnTunnelState.DOWN -> {
                                    verificationCycle = null
                                    if (isReconnecting || isServiceReconnecting) {
                                        ProtonLogger.d(TAG, "Tunnel DOWN during reconnection, preserving server state")
                                    } else if (previousState != VpnTunnelState.DOWN ||
                                        _vpnState.value != VpnState.DISCONNECTED) {
                                        handleTunnelStateChange(VpnTunnelState.DOWN)
                                    }
                                }
                            }
                        }
                    }
                    ProtonVpnService.ACTION_STATS_UPDATED -> {
                        val serverId = intent.getStringExtra(ProtonVpnService.EXTRA_LOGICAL_SERVER_ID)
                        if (serverId != null && serverId != currentServerId && _vpnState.value != VpnState.DISCONNECTED && _vpnState.value != VpnState.DISCONNECTING) {
                            currentServerId = serverId
                            applicationScope.launch {
                                val resolved = vpnRepositoryProvider.get().getCachedServers().find { it.id == serverId }
                                if (resolved != null) {
                                    connectedServerState.setConnectedServer(resolved)
                                }
                            }
                        }

                        _speed.value = intent.getStringExtra(ProtonVpnService.EXTRA_SPEED)
                _trafficRx.value = intent.getStringExtra(ProtonVpnService.EXTRA_TRAFFIC_RX)
                _trafficTx.value = intent.getStringExtra(ProtonVpnService.EXTRA_TRAFFIC_TX)
                    }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Query current VPN state from service on startup
        systemContextWrapper.queryVpnState()

        // Monitor settings changes and update the service accordingly.
        // We use a single coroutine with a small initial delay to avoid competing 
        // with the main thread during critical app boot/injection window.
        applicationScope.launch {
            delay(1000.milliseconds)
            merge(
                settingsManager.notificationsEnabled.map { Unit },
                settingsManager.killSwitchEnabled.map { Unit },
                settingsManager.sentryNonFatalEnabled.map { Unit },
                settingsManager.analyticsEnabled.map { Unit },
                settingsManager.connectionVerificationMode.map { Unit },
                settingsManager.connectionVerificationRequired.map { Unit },
                settingsManager.connectionFailureDetection.map { Unit },
                settingsManager.connectionAutoReconnect.map { Unit },
            ).collectLatest {
                updateServiceSettings()
            }
        }

        applicationScope.launch {
            delay(1500.milliseconds) // Staggered initialization
            val session = sessionDao.getSession()
            if (session != null) {
                updateCertificateState(session.wgCertificate)
                if (_certState.value !is CertificateState.Valid) {
                    checkAndRefreshCertificateProactively()
                }
            }
        }
    }

    internal fun handleTunnelStateChange(newState: VpnTunnelState) {
        _rawTunnelState.value = newState
        _tunnelState.value = newState
        when (newState) {
            VpnTunnelState.UP -> {
                isPaused = false
                pauseJob?.cancel()
                applicationScope.launch { settingsManager.setPauseEndTime(0) }
                
                checkAndRefreshCertificateProactively()
                startTunnelVerification()
            }
            VpnTunnelState.DOWN -> {
                verificationJob?.cancel()
                if (!isReconnecting) {
                    updateVpnState(VpnState.DISCONNECTED)
                    currentServerId = null
                    connectedServerState.setConnectedServer(null)
                    _speed.value = null
        _trafficRx.value = null
        _trafficTx.value = null
                } else {
                    updateVpnState(VpnState.CONNECTING)
                }
            }
        }
    }

    private fun startTunnelVerification() {
        if (verificationJob?.isActive == true) {
            ProtonLogger.v(TAG, "Connectivity verification is already running")
            return
        }

        verificationJob = applicationScope.launch {
            val mode = settingsManager.connectionVerificationMode.first()
            val required = settingsManager.connectionVerificationRequired.first()
            if (mode == ConnectionVerificationMode.DISABLED) {
                verificationCycle = null
                updateVpnState(VpnState.CONNECTED)
                systemContextWrapper.setVpnVerified()
                return@launch
            }

            val cycle = verificationCycle ?: vpnNetworkMonitor.beginVerificationCycle().also {
                verificationCycle = it
            }
            if (required) updateVpnState(VpnState.VERIFYING)
            else updateVpnState(VpnState.CONNECTED)
            ProtonLogger.d(TAG, "Checking VPN usability (${mode.name.lowercase()}, required=$required)")

            try {
                val usable = vpnNetworkMonitor.awaitUsable(
                    cycle = cycle,
                    timeout = mode.verificationTimeoutMs.milliseconds,
                    retryDelay = mode.verificationRetryDelayMs.milliseconds,
                )
                if (_tunnelState.value != VpnTunnelState.UP) return@launch

                if (usable) {
                    ProtonLogger.i(TAG, "VPN connectivity confirmed")
                } else {
                    ProtonLogger.w(TAG, "VPN usability probe timed out; keeping the established tunnel")
                }
                updateVpnState(VpnState.CONNECTED)
                systemContextWrapper.setVpnVerified()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                ProtonLogger.w(TAG, "VPN usability verification failed: ${error.message}")
                if (_tunnelState.value == VpnTunnelState.UP) {
                    updateVpnState(VpnState.CONNECTED)
                    systemContextWrapper.setVpnVerified()
                }
            } finally {
                verificationCycle = null
            }
        }
    }

    private fun updateCertificateState(certPem: String?) {
        if (certPem.isNullOrEmpty()) {
            _certState.value = CertificateState.Expired
            return
        }
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val x509 = cf.generateCertificate(ByteArrayInputStream(certPem.toByteArray())) as X509Certificate
            val now = System.currentTimeMillis()
            val expiry = x509.notAfter.time

            if (now >= expiry) {
                _certState.value = CertificateState.Expired
            } else if (expiry - now < REFRESH_THRESHOLD_MS) {
                val hours = ((expiry - now) / (3600 * 1000L)).toInt()
                _certState.value = CertificateState.ExpiringSoon(hours)
            } else {
                _certState.value = CertificateState.Valid
            }
        } catch (e: Exception) {
            _certState.value = CertificateState.Expired
        }
    }

    private suspend fun performCertificateRefresh(force: Boolean = false): Result<String> {
        val result = performCertificateRefreshInternal(force)
        
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            if (error is HttpException && error.code() == 401) {
                ProtonLogger.w(TAG, "Certificate refresh failed with 401. Attempting session refresh.")
                val session = sessionDao.getSession()
                if (session != null) {
                    val authResult = authRepositoryProvider.get().refreshSession(session.sessionId, session.refreshToken)
                    if (authResult.isSuccess) {
                        ProtonLogger.i(TAG, "Session refreshed successfully. Retrying certificate refresh.")
                        return performCertificateRefreshInternal(force)
                    }
                }
            }
        }
        
        return result
    }

    private suspend fun performCertificateRefreshInternal(force: Boolean = false): Result<String> = refreshMutex.withLock {
        val currentSession = sessionDao.getSession() ?: return Result.failure<String>(Exception("No session")).also {
            ProtonLogger.e(TAG, "Certificate refresh failed: No active session found in database")
        }

        val previousState = _certState.value
        _certState.value = CertificateState.Refreshing
        ProtonLogger.i(TAG, "Starting certificate refresh (force=$force, previous state: $previousState)")

        try {
            ProtonLogger.v(TAG, "Requesting new VPN keypair and certificate from API")

            val mode = if (currentSession.isExtendedCertEnabled) "persistent" else null
            val result = vpnRepositoryProvider.get().registerWireGuardKey(
                accessToken = currentSession.accessToken,
                sessionId = currentSession.sessionId,
                mode = mode
            )

            if (result.isSuccess) {
                val pair = result.getOrNull()
                val response = pair?.first
                val keyPair = pair?.second
                val newCert = response?.certificate
                
                if (newCert != null && keyPair != null) {
                    ProtonLogger.i(TAG, "Successfully obtained WireGuard certificate")

                    // Metrics
                    ProtonLogger.recordCount("cert_refresh_success", 1.0)

                    // Persist the NEW private key along with the NEW certificate and expiration times
                    sessionDao.updateVpnKeys(
                        privateKey = keyPair.privateKeyX25519,
                        publicKeyPem = keyPair.publicKeyPem,
                        certificate = newCert,
                        expiresAt = response.expirationTime ?: 0,
                        refreshAt = response.refreshTime ?: 0
                    )

                    updateCertificateState(newCert)
                    Result.success(newCert)
                } else {
                    ProtonLogger.e(TAG, "Server returned success but certificate or keys are missing")
                    _certState.value = previousState
                    Result.failure(Exception("Empty certificate in response"))
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                ProtonLogger.e(TAG, "Failed to register WireGuard key with Proton API: $error", result.exceptionOrNull())

                // Metrics
                ProtonLogger.recordCount("cert_refresh_error", 1.0)

                val isFullyExpired = previousState is CertificateState.Expired ||
                        (previousState is CertificateState.RefreshFailed && previousState.isFullyExpired)
                _certState.value = CertificateState.RefreshFailed(error, isFullyExpired)
                Result.failure(result.exceptionOrNull() ?: Exception(error))
            }
        } finally {
        }
    }

    fun checkAndRefreshCertificateProactively(force: Boolean = false) {
        if (force) {
            refreshJob?.cancel()
        } else if (refreshJob?.isActive == true) {
            return
        }
        refreshJob = applicationScope.launch {
            var firstRun = force
            var currentRetryDelay = 60000L // Start retrying after 1 minute
            while (isActive) {
                val session = sessionDao.getSession() ?: break
                updateCertificateState(session.wgCertificate)

                val isConnected = _tunnelState.value == VpnTunnelState.UP

                if (_certState.value is CertificateState.Valid) {
                    // All good, check again in 2 hours
                    delay(PERIODIC_REFRESH_MS.milliseconds)
                    currentRetryDelay = 5000L
                    continue
                }

                // If VPN is inactive, we only refresh certificate when connecting or already connected.
                if (!firstRun && !isConnected && !_isConnecting.value) {
                    ProtonLogger.d(TAG, "Proactive refresh: VPN inactive. Skipping background periodic refresh.")
                    delay(PERIODIC_REFRESH_MS.milliseconds)
                    continue
                }

                firstRun = false
                ProtonLogger.d(TAG, "Proactive refresh starting (cert state: ${_certState.value})")
                val result = performCertificateRefresh(force = false)
                
                if (result.isSuccess) {
                    currentRetryDelay = 5000L
                    delay(PERIODIC_REFRESH_MS.milliseconds)
                } else {
                    // API access is expected to be preserved, so we retry with backoff.
                    // This covers cases where internet is temporarily down.
                    ProtonLogger.w(TAG, "Proactive refresh failed, retrying in ${currentRetryDelay}ms")
                    delay(currentRetryDelay.milliseconds)
                    currentRetryDelay = (currentRetryDelay * 2).coerceAtMost(RETRY_DELAY_MS)
                }
            }
        }
    }

    fun isEffectivelyExpired(): Boolean {
        val state = _certState.value
        return state is CertificateState.Expired || (state is CertificateState.RefreshFailed && state.isFullyExpired)
    }

    fun simulateExpiredCertificate() {
        _certState.value = CertificateState.Expired
    }

    suspend fun forceRefreshCertificate(): Result<String> {
        return performCertificateRefresh(force = true)
    }

    private suspend fun updateServiceSettings() {
        // If VPN is paused, we shouldn't be updating or trying to reconnect
        if (isPaused || settingsManager.pauseEndTime.first() > System.currentTimeMillis()) {
            ProtonLogger.d(TAG, "Skipping service settings update because VPN is paused.")
            return
        }

        systemContextWrapper.updateVpnSettings(
            notificationsEnabled = settingsManager.notificationsEnabled.first(),
            killSwitchEnabled = settingsManager.killSwitchEnabled.first(),
            nonFatalEnabled = settingsManager.sentryNonFatalEnabled.first(),
            analyticsEnabled = settingsManager.analyticsEnabled.first(),
            verificationMode = settingsManager.connectionVerificationMode.first(),
            verificationRequired = settingsManager.connectionVerificationRequired.first(),
            failureDetectionEnabled = settingsManager.connectionFailureDetection.first(),
            autoReconnectEnabled = settingsManager.connectionAutoReconnect.first(),
        )
    }

    fun connect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null,
        logicalServer: LogicalServer? = null,
        forceFallback: Boolean = false,
        multiHopEntryServer: PhysicalServer? = null
    ) {
        // Immediate UI update to avoid "VPN" placeholder
        if (logicalServer != null) {
            connectedServerState.setConnectedServer(logicalServer)
        }
        
        applicationScope.launch {
            if (isPaused || settingsManager.pauseEndTime.first() > System.currentTimeMillis()) {
                val persistentEnd = settingsManager.pauseEndTime.first()
                ProtonLogger.d(TAG, "Connection blocked: VPN is currently paused (Local isPaused: $isPaused, Persistent: $persistentEnd)")
                return@launch
            }

            if (currentServerId == logicalServerId && _tunnelState.value == VpnTunnelState.UP) {
                ProtonLogger.d(TAG, "Already connected to $logicalServerId")
                return@launch
            }

            connectionJob?.cancel()
            verificationJob?.cancel()

            _connectionWarning.value = null
            updateVpnState(VpnState.CONNECTING)
            _isConnecting.value = true
            
            connectionJob = applicationScope.launch(dispatcherProvider.io()) {
                currentServerId = logicalServerId

                // Resolve logical server if not provided earlier
                if (connectedServerState.connectedServer.value?.id != logicalServerId) {
                    val resolved = vpnRepositoryProvider.get().getCachedServers().find { it.id == logicalServerId }
                    connectedServerState.setConnectedServer(resolved)
                }

                connectInternal(logicalServerId, server, session, overridePort, overrideObfuscation, obfuscationParams, forceFallback, multiHopEntryServer)

                // Track connection attempt
                ProtonLogger.recordCount("vpn_connection_attempt", 1.0)
            }
        }
    }

    private fun normalizeIpv4Address(address: String): String? = runCatching {
        InetAddress.getByName(address)
            .takeIf { it is Inet4Address }
            ?.hostAddress
    }.getOrNull()

    private suspend fun connectInternal(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null,
        forceFallback: Boolean = false,
        multiHopEntryServer: PhysicalServer? = null
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        try {
            lastConnectionRequest = LastConnectionRequest(
                logicalServerId = logicalServerId,
                server = server,
                overridePort = overridePort,
                overrideObfuscation = overrideObfuscation,
                obfuscationParams = obfuscationParams,
                multiHopEntryServer = multiHopEntryServer
            )

            val serverLogInfo = "${server.id} (Domain: ${server.domain}, LogicalID: $logicalServerId)"
            ProtonLogger.i(TAG, "Initiating connection to server: $serverLogInfo")
            ProtonLogger.addSentryBreadcrumb(TAG, "VPN Connection Step: Start ($serverLogInfo)", "INFO", "vpn.connect")

            _isConnecting.value = true
            var currentSession = session

            // Proactively refresh certificate if it's not valid (Expired or ExpiringSoon)
            updateCertificateState(currentSession.wgCertificate)
            if (_certState.value !is CertificateState.Valid) {
                ProtonLogger.i(TAG, "Certificate state is ${_certState.value}, attempting refresh before connection.")
                performCertificateRefresh()

                if (isEffectivelyExpired()) {
                    ProtonLogger.w(TAG, "Certificate is still effectively expired after refresh attempt. Proceeding anyway as Proton API might allow grace period.")
                }
                
                // Refresh session from DB to get the new certificate and any other potential updates
                currentSession = sessionDao.getSession() ?: currentSession
            }

            val wgPrivateKeyB64 = currentSession.wgPrivateKey ?: throw Exception("Offline VPN private key missing!").also {
                ProtonLogger.e(TAG, "Critical: VPN Private Key is null in session data")
            }
            var targetIp: String? = null
            val proxyChainEnabled = settingsManager.proxyChainEnabled.first()
            val proxyChainConfig = settingsManager.proxyChainConfig.first().trim()
            if (proxyChainEnabled && !ProxyLinkParser.isValid(proxyChainConfig)) {
                throw ExpectedConnectionException(
                    ConnectionWarning.InvalidProxyConfiguration,
                    "Proxy chain is enabled but its vless:// or vmess:// configuration is invalid"
                )
            }

            // Resolve and probe over the physical network before VpnService creates/reloads TUN.
            // This prevents server switching from routing the next endpoint lookup into the old VPN.
            val preflight = if (forceFallback) null else runCatching {
                vpnNetworkMonitor.prepareUnderlyingConnection(
                    endpointHost = server.domain,
                    proxyChainConfig = proxyChainConfig.takeIf { proxyChainEnabled },
                )
            }.getOrElse { error ->
                if (settingsManager.connectionPreflightRequired.first()) throw error
                ProtonLogger.w(TAG, "Connection preflight failed but is optional: ${error.message}")
                null
            }
            val proxyServerOverrides = preflight?.proxyServerOverrides.orEmpty()

            if (forceFallback) {
                // Skip DNS entirely and use exitIp only when it is IPv4.
                val fallbackIp = server.exitIp?.takeIf(String::isNotBlank)
                targetIp = fallbackIp?.let(::normalizeIpv4Address)
                if (targetIp != null) {
                    ProtonLogger.i(TAG, "forceFallback=true: skipping DNS resolution, using IPv4 exitIp: $targetIp")
                } else if (fallbackIp != null) {
                    throw ExpectedConnectionException(
                        ConnectionWarning.Ipv6OnlyEndpoint,
                        "The fallback endpoint is not reachable over IPv4"
                    )
                } else {
                    _isConnecting.value = false
                    _tunnelState.value = VpnTunnelState.DOWN
                    throw Exception("forceFallback=true but no exitIp available for ${server.domain}").also {
                        ProtonLogger.e(TAG, it.message!!)
                    }
                }
            } else {
                if (preflight != null) {
                    targetIp = preflight.endpointIpv4
                    ProtonLogger.i(TAG, "Connection preflight resolved ${server.domain} to IPv4 $targetIp on the underlying network")
                } else {
                    // Compatibility fallback for environments where a physical Network is not exposed.
                    ProtonLogger.d(TAG, "Resolving IPv4 for ${server.domain} (Max retries: $DNS_RETRY_COUNT)")
                    for (i in 1..DNS_RETRY_COUNT) {
                        if (!isActive) break
                        try {
                            targetIp = InetAddress.getAllByName(server.domain)
                                .filterIsInstance<Inet4Address>()
                                .firstOrNull()
                                ?.hostAddress
                            if (targetIp != null) {
                                ProtonLogger.i(TAG, "DNS resolved ${server.domain} to IPv4 $targetIp on attempt $i")
                                break
                            }
                            ProtonLogger.w(TAG, "DNS retry $i returned no IPv4 address for ${server.domain}")
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (e: Exception) {
                            ProtonLogger.w(TAG, "DNS retry $i failed for ${server.domain}: ${e.message}")
                        }
                        if (i < DNS_RETRY_COUNT) delay((DNS_RETRY_DELAY_MS * i).milliseconds)
                    }
                }

                if (targetIp == null) {
                    val fallbackIp = server.exitIp?.takeIf(String::isNotBlank)
                    targetIp = fallbackIp?.let(::normalizeIpv4Address)
                    if (targetIp != null) {
                        ProtonLogger.w(TAG, "DNS returned no IPv4 for ${server.domain}; using IPv4 exitIp: $targetIp")
                    } else if (fallbackIp != null) {
                        throw ExpectedConnectionException(
                            ConnectionWarning.Ipv6OnlyEndpoint,
                            "The selected VPN server is only reachable over IPv6"
                        )
                    } else {
                        _isConnecting.value = false
                        _tunnelState.value = VpnTunnelState.DOWN
                        throw Exception("DNS resolution failed for ${server.domain} after $DNS_RETRY_COUNT attempts")
                    }
                }
            }

            val serverPubKey = server.wgPublicKey ?: throw Exception("Missing WG Public Key for Server").also {
                ProtonLogger.e(TAG, "Critical: Server ${server.id} has no WireGuard Public Key")
            }
            
            val splitTunnelingEnabled = settingsManager.splitTunnelingEnabled.first()
            val stMode = settingsManager.splitTunnelingMode.first()
            val isIncludeMode = splitTunnelingEnabled && stMode == "include"
            val allowLan = settingsManager.allowLanEnabled.first()

            val selectedApps = if (splitTunnelingEnabled) settingsManager.excludedApps.first() else emptySet()
            val selectedIps = if (splitTunnelingEnabled) settingsManager.excludedIps.first().toMutableSet() else mutableSetOf()
            val selectedDomains = if (splitTunnelingEnabled) settingsManager.excludedDomains.first() else emptySet()

            ProtonLogger.d(TAG, "Split Tunneling: enabled=$splitTunnelingEnabled, mode=$stMode, allowLan=$allowLan, apps=${selectedApps.size}, IPs=${selectedIps.size}, domains=${selectedDomains.size}")

            // Resolve split tunneling domains
            if (splitTunnelingEnabled && selectedDomains.isNotEmpty()) {
                ProtonLogger.i(TAG, "Resolving ${selectedDomains.size} split-tunneling domains...")
                SplitTunnelingDomainRule.exactDomains(selectedDomains).forEach { domain ->
                    try {
                        val underlayIp = vpnNetworkMonitor.resolveIpv4OnUnderlying(domain)
                        val addresses = underlayIp?.let(::listOf)
                            ?: InetAddress.getAllByName(domain)
                                .filterIsInstance<Inet4Address>()
                                .mapNotNull(InetAddress::getHostAddress)
                        addresses.forEach { ip ->
                            selectedIps.add("$ip/32")
                            ProtonLogger.v(TAG, "Split-tunnel domain $domain resolved to $ip")
                        }
                    } catch (e: Exception) {
                        ProtonLogger.w(TAG, "Failed to resolve split-tunneling domain $domain: ${e.message}")
                    }
                }
            }

            val selectedPort = overridePort?.takeIf { it != 0 } ?: settingsManager.vpnPort.first().let { port ->
                if (port == 0) {
                    val p = listOf(443, 123, 1194, 51820).random()
                    ProtonLogger.d(TAG, "Auto-port selected: $p")
                    p
                } else port
            }
            val multiHopEntry = multiHopEntryServer?.let { entryServer ->
                val entryIp = runCatching {
                    vpnNetworkMonitor.prepareUnderlyingConnection(
                        endpointHost = entryServer.domain,
                        proxyChainConfig = proxyChainConfig.takeIf { proxyChainEnabled }
                    ).endpointIpv4
                }.getOrNull()
                    ?: vpnNetworkMonitor.resolveIpv4OnUnderlying(entryServer.domain)
                    ?: entryServer.exitIp?.let(::normalizeIpv4Address)
                    ?: throw Exception("Unable to resolve Multi Hop entry ${entryServer.domain} over IPv4")
                val entryPublicKey = entryServer.wgPublicKey
                    ?: throw Exception("Missing WG Public Key for Multi Hop entry ${entryServer.id}")
                MultiHopEndpoint(entryPublicKey, entryIp, selectedPort)
            }

            val isObfuscationEnabled = !proxyChainEnabled &&
                (overrideObfuscation ?: settingsManager.obfuscationEnabled.first())

            val torModeEnabled = settingsManager.torModeEnabled.first()
            ProtonLogger.i(
                TAG,
                "Connection parameters: Port=$selectedPort, AWG obfuscation=$isObfuscationEnabled, proxy chain=$proxyChainEnabled, Tor=$torModeEnabled"
            )

            val params = if (isObfuscationEnabled) {
                obfuscationParams ?: ObfuscationParams(
                    jc = settingsManager.awgJc.first(), jmin = settingsManager.awgJmin.first(), jmax = settingsManager.awgJmax.first(),
                    s1 = settingsManager.awgS1.first(), s2 = settingsManager.awgS2.first(),
                    s3 = settingsManager.awgS3.first(), s4 = settingsManager.awgS4.first(),
                    h1 = settingsManager.awgH1.first(), h2 = settingsManager.awgH2.first(), h3 = settingsManager.awgH3.first(), h4 = settingsManager.awgH4.first(),
                    i1 = settingsManager.awgI1.first(), i2 = settingsManager.awgI2.first(), i3 = settingsManager.awgI3.first(), i4 = settingsManager.awgI4.first(), i5 = settingsManager.awgI5.first(),
                    headerProtectionKey = settingsManager.awgHeaderProtectionKey.first(),
                    contentPaddingAddition = settingsManager.awgContentPaddingAddition.first(),
                    rekeyAfterTime = settingsManager.awgRekeyAfterTime.first(),
                    rekeyTimeout = settingsManager.awgRekeyTimeout.first(),
                    rejectAfterTime = settingsManager.awgRejectAfterTime.first(),
                    keepaliveTimeout = settingsManager.awgKeepaliveTimeout.first(),
                    maxHandshakeAttempts = settingsManager.awgMaxHandshakeAttempts.first(),
                    persistentKeepalive = settingsManager.awgPersistentKeepalive.first()
                )
            } else {
                ObfuscationParams(0, 0, 0, 0, 0, 0, 0, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
            }

            // Use assigned IP/DNS from session if available, fallback to defaults.
            // Paid users (Tier > 0) are often assigned unique internal IPs by the Proton API.
            val localIp = currentSession.vpnIpv4 ?: PROTON_CLIENT_IP
            val fallbackDns = currentSession.vpnDns?.split(",")?.firstOrNull() ?: PROTON_DNS_IP

            // Retrieve Custom DNS IP or fallback to Proton Assigned/Default
            val userDns = settingsManager.customDns.first().trim()
            val isValidDns = userDns.isNotEmpty() && normalizeIpv4Address(userDns) != null
            if (userDns.isNotEmpty() && !isValidDns) {
                ProtonLogger.w(TAG, "Custom DNS is not a numeric IPv4 address; falling back to the Proton DNS server")
            }
            val activeDns = if (isValidDns) userDns else fallbackDns
            ProtonLogger.i(TAG, "Using DNS Server: $activeDns, Client IP: $localIp")

            ProtonLogger.addSentryBreadcrumb(TAG, "VPN Connection Step: Building Config", "DEBUG", "vpn.connect")
            val configStr = awgBoxConfigGenerator.buildConfig(
                serverPublicKey = serverPubKey,
                privateKey = wgPrivateKeyB64,
                localIp = localIp,
                dnsServer = activeDns,
                targetIp = targetIp,
                isIncludeMode = isIncludeMode,
                allowLan = allowLan,
                // Android app ownership is applied directly by AwgBoxPlatform. Keep the
                // selection free of implementation packages so Exclude and Include cannot leak
                // package filters into one another during an engine reload.
                selectedApps = selectedApps,
                selectedIps = selectedIps,
                selectedDomains = selectedDomains,
                port = selectedPort,
                certificate = currentSession.wgCertificate,
                obfuscationParams = params,
                proxyChainConfig = proxyChainConfig.takeIf { proxyChainEnabled },
                netShieldRuleSets = localNetShield.activeRuleSets(settingsManager.netShieldLevel.first()),
                proxyServerOverrides = proxyServerOverrides,
                torModeEnabled = torModeEnabled,
                torDataDirectory = File(context.noBackupFilesDir, "tor").absolutePath,
                torExecutablePath = File(context.applicationInfo.nativeLibraryDir, "libtor.so").absolutePath,
                multiHopEntry = multiHopEntry
            )
            
            ProtonLogger.d(TAG, "Generated awgbox config (length=${configStr.length}, endpoint=$targetIp:$selectedPort)")

            val sessionId = System.currentTimeMillis()
            ProtonLogger.addSentryBreadcrumb(TAG, "VPN Connection Step: Starting Service (Session: $sessionId)", "INFO", "vpn.connect")
            systemContextWrapper.startVpnService(
                configStr = configStr,
                logicalServerId = logicalServerId,
                sessionId = sessionId,
                notificationsEnabled = settingsManager.notificationsEnabled.first(),
                killSwitchEnabled = settingsManager.killSwitchEnabled.first(),
                verificationMode = settingsManager.connectionVerificationMode.first(),
                verificationRequired = settingsManager.connectionVerificationRequired.first(),
                failureDetectionEnabled = settingsManager.connectionFailureDetection.first(),
                autoReconnectEnabled = settingsManager.connectionAutoReconnect.first(),
                splitTunnelingEnabled = splitTunnelingEnabled,
                splitTunnelingMode = stMode,
                excludedApps = selectedApps,
                excludedIps = selectedIps
            )

            ProtonLogger.i(TAG, "VPN start command issued successfully")
            
            // Track connection success
            ProtonLogger.recordCount("vpn_connection_success", 1.0)
            
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            ProtonLogger.d(TAG, "VPN connection preparation cancelled")
            throw cancellation
        } catch (e: ExpectedConnectionException) {
            // This is an expected, actionable configuration/network condition. Keep it out of
            // Sentry and expose it to the dashboard as a warning instead of reporting a crash.
            ProtonLogger.w(TAG, "VPN connection blocked: ${e.message}")
            _connectionWarning.value = e.warning
            _isConnecting.value = false
            _tunnelState.value = VpnTunnelState.DOWN
            updateVpnState(VpnState.DISCONNECTED)
            connectedServerState.setConnectedServer(null)
            currentServerId = null
            Result.failure(e)
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Failed to connect to VPN", e)
            ProtonLogger.addSentryBreadcrumb(TAG, "VPN Connection Failed: ${e.message}", "ERROR", "vpn.error")

            // Track connection failure
            ProtonLogger.recordCount("vpn_connection_failure", 1.0)

            _isConnecting.value = false
            _tunnelState.value = VpnTunnelState.DOWN
            connectedServerState.setConnectedServer(null)
            currentServerId = null
            Result.failure(e)
        }
    }

    fun reconnect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null,
        logicalServer: LogicalServer? = null,
        forceFallback: Boolean = false,
        multiHopEntryServer: PhysicalServer? = null
    ) {
        // Immediate UI update
        if (logicalServer != null) {
            connectedServerState.setConnectedServer(logicalServer)
        }

        applicationScope.launch {
            if (isPaused || settingsManager.pauseEndTime.first() > System.currentTimeMillis()) {
                val persistentEnd = settingsManager.pauseEndTime.first()
                ProtonLogger.d(TAG, "Reconnect blocked: VPN is currently paused (Local isPaused: $isPaused, Persistent: $persistentEnd)")
                return@launch
            }

            // Only skip if we're already connecting (to avoid multiple rapid clicks)
            if (_isConnecting.value) {
                ProtonLogger.d(TAG, "Reconnect skipped: Already in a connecting state.")
                return@launch
            }

            connectionJob?.cancel()
            verificationJob?.cancel()

            _connectionWarning.value = null
            updateVpnState(VpnState.CONNECTING)
            _isConnecting.value = true

            connectionJob = applicationScope.launch {
                try {
                    isReconnecting = true
                    _isConnecting.value = true
                    currentServerId = logicalServerId

                    // Resolve logical server if not provided earlier
                    if (connectedServerState.connectedServer.value?.id != logicalServerId) {
                        val resolved = vpnRepositoryProvider.get().getCachedServers().find { it.id == logicalServerId }
                        connectedServerState.setConnectedServer(resolved)
                    }

                    disconnectInternal()
                    try {
                        withTimeout(5000.milliseconds) {
                            _rawTunnelState.first { it == VpnTunnelState.DOWN }
                        }
                    } catch (_: Exception) {
                    }
                    delay(500.milliseconds)
                    connectInternal(logicalServerId, server, session, overridePort, overrideObfuscation, obfuscationParams, forceFallback, multiHopEntryServer)
                } finally {
                    isReconnecting = false
                }
            }
        }
    }

    /**
     * True when the current tunnel was established by this process, so its parameters (server,
     * profile port and obfuscation overrides) can be replayed by [reconnectCurrent].
     */
    fun canReconnectCurrent(): Boolean = lastConnectionRequest != null

    /**
     * Re-establishes the active tunnel with the same target and overrides, picking up connection
     * settings that were changed after it was established.
     */
    fun reconnectCurrent() {
        val request = lastConnectionRequest ?: run {
            ProtonLogger.w(TAG, "Reconnect requested, but no previous connection is known")
            return
        }

        applicationScope.launch {
            val session = sessionDao.getSession() ?: run {
                ProtonLogger.w(TAG, "Reconnect requested without an active session")
                return@launch
            }
            ProtonLogger.action(TAG, "Reconnecting to apply changed connection settings")
            reconnect(
                logicalServerId = request.logicalServerId,
                server = request.server,
                session = session,
                overridePort = request.overridePort,
                overrideObfuscation = request.overrideObfuscation,
                obfuscationParams = request.obfuscationParams,
                logicalServer = connectedServerState.connectedServer.value,
                multiHopEntryServer = request.multiHopEntryServer
            )
        }
    }

    fun disconnect() {
        ProtonLogger.action(TAG, "User clicked Disconnect")
        connectionJob?.cancel()
        verificationJob?.cancel()
        pauseJob?.cancel()
        _isConnecting.value = false
        isPaused = false
        applicationScope.launch { settingsManager.setPauseEndTime(0) }

        applicationScope.launch {
            isReconnecting = false
            currentServerId = null
            updateVpnState(VpnState.DISCONNECTING)
            disconnectInternal()
        }
    }

    fun pauseVpn(durationMs: Long) {
        ProtonLogger.action(TAG, "Pausing VPN for $durationMs ms")
        val endTime = System.currentTimeMillis() + durationMs
        isPaused = true
        
        pauseJob?.cancel()
        pauseJob = applicationScope.launch {
            settingsManager.setPauseEndTime(endTime)
            
            // Critical: Ensure no other connection jobs are running
            connectionJob?.cancel()
            verificationJob?.cancel()

            disconnectInternal()
        }
    }

    suspend fun resumeVpn() {
        val persistentPauseEnd = settingsManager.pauseEndTime.first()
        if (!isPaused && persistentPauseEnd == 0L) return

        ProtonLogger.action(TAG, "Resuming VPN (Local isPaused: $isPaused, Persistent: $persistentPauseEnd)")
        isPaused = false
        pauseJob?.cancel()
        settingsManager.setPauseEndTime(0)
    }

    private suspend fun disconnectInternal() = withContext(dispatcherProvider.io()) {
        systemContextWrapper.stopVpnService()
    }

    /**
     * Connect & Go: Centralized logic to wait for the VPN tunnel to be ready
     * and then open the specified URL in the system browser.
     */
    fun awaitTunnelAndOpenUrl(url: String) {
        if (url.isEmpty()) return

        var finalUrl = url.trim()
        if (finalUrl.isNotEmpty() && !finalUrl.contains("://")) {
            finalUrl = "https://$finalUrl"
        }

        val targetUrl = finalUrl

        applicationScope.launch(dispatcherProvider.main()) {
            ProtonLogger.d(TAG, "Connect & Go: Waiting for tunnel UP to open URL: $targetUrl")
            try {
                // Initial delay to allow the connection attempt to start and set isConnecting=true
                delay(1500.milliseconds)

                withTimeout(40000.milliseconds) {
                    // 1. If we are currently in the middle of connecting, wait for it to finish
                    if (_isConnecting.value) {
                        ProtonLogger.d(TAG, "Connect & Go: VPN is connecting, waiting...")
                        _isConnecting.first { !it }
                    }
                    
                    // 2. Then wait for the tunnel state to be UP
                    ProtonLogger.d(TAG, "Connect & Go: VPN attempt finished, waiting for UP state...")
                    _tunnelState.first { it == VpnTunnelState.UP }
                }

                // 3. Extra delay to ensure routing and DNS are fully established and browser can reach the site
                ProtonLogger.d(TAG, "Connect & Go: Tunnel is UP, waiting for routing stabilization...")
                delay(3000.milliseconds)

                if (_tunnelState.value == VpnTunnelState.UP) {
                    val intent = Intent(Intent.ACTION_VIEW, targetUrl.toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ProtonLogger.d(TAG, "Connect & Go: URL opened successfully: $targetUrl")
                } else {
                    ProtonLogger.w(TAG, "Connect & Go: Tunnel is not UP anymore, skipping URL open.")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    // A connection that never comes up is already surfaced by the tunnel state; the
                    // unopened URL is a consequence, not a separate defect.
                    ProtonLogger.w(TAG, "Connect & Go: Timed out waiting for VPN to connect for URL: $targetUrl")
                } else {
                    ProtonLogger.e(TAG, "Connect & Go: Failed to handle URL: $targetUrl", e)
                }
            }
        }
    }
}
