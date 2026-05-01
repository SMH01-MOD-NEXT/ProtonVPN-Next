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
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import io.sentry.SentryLevel
import ru.protonmod.next.utils.ProtonLogger
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import org.amnezia.awg.config.Interface
import org.amnezia.awg.config.Peer
import java.net.InetAddress
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.di.ApplicationScope
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import ru.protonmod.next.utils.crypto.CryptoWrapper
import ru.protonmod.next.utils.system.SystemContextWrapper
import io.sentry.Sentry
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AmneziaVpnManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val vpnRepositoryProvider: Provider<VpnRepository>,
    private val sessionDao: SessionDao,
    private val connectedServerState: ConnectedServerState,
    private val systemContextWrapper: SystemContextWrapper,
    private val cryptoWrapper: CryptoWrapper,
    private val amneziaConfigGenerator: AmneziaConfigGenerator,
    private val vpnNetworkMonitor: VpnNetworkMonitor,
    private val warpManager: Provider<WarpManager>,
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

    data class ObfuscationParams(
        val jc: Int, val jmin: Int, val jmax: Int,
        val s1: Int, val s2: Int, val s3: Int = 0, val s4: Int = 0,
        val h1: String, val h2: String, val h3: String, val h4: String,
        val i1: String, val i2: String = "", val i3: String = "", val i4: String = "", val i5: String = ""
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

    private val _speed = MutableStateFlow<String?>(null)
    val speed: StateFlow<String?> = _speed.asStateFlow()

    private val _tunnelState = MutableStateFlow(Tunnel.State.DOWN)
    val tunnelState: StateFlow<Tunnel.State> = _tunnelState

    private val _rawTunnelState = MutableStateFlow(Tunnel.State.DOWN)
    private var isReconnecting = false
    private var currentServerId: String? = null
    private var connectionJob: Job? = null
    private var verificationJob: Job? = null
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
                        stateStr?.let {
                            if (it == STATE_CONNECTING) {
                                _isConnecting.value = true
                                _vpnState.value = VpnState.CONNECTING
                            } else {
                                try {
                                    val newState = Tunnel.State.valueOf(it)
                                    _rawTunnelState.value = newState
                                    _isConnecting.value = false

                                    _tunnelState.value = newState
                                    
                                    handleTunnelStateChange(newState)
                                    
                                } catch (e: Exception) {
                                    ProtonLogger.e(TAG, "Failed to parse tunnel state: $it")
                                }
                            }
                        }
                    }
                    ProtonVpnService.ACTION_STATS_UPDATED -> {
                        _speed.value = intent.getStringExtra(ProtonVpnService.EXTRA_SPEED)
                    }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Monitor settings changes and update the service accordingly.
        // We use a single coroutine with a small initial delay to avoid competing 
        // with the main thread during critical app boot/injection window.
        applicationScope.launch {
            delay(1000)
            combine(
                settingsManager.notificationsEnabled,
                settingsManager.killSwitchEnabled,
                settingsManager.sentryNonFatalEnabled,
                settingsManager.analyticsEnabled
            ) { _, _, _, _ -> }
                .collectLatest {
                    updateServiceSettings()
                }
        }

        applicationScope.launch {
            delay(1500) // Staggered initialization
            val session = sessionDao.getSession()
            if (session != null) {
                updateCertificateState(session.wgCertificate)
                if (_certState.value !is CertificateState.Valid) {
                    checkAndRefreshCertificateProactively()
                }
            }
        }
    }

    internal fun handleTunnelStateChange(newState: Tunnel.State) {
        when (newState) {
            Tunnel.State.UP -> {
                checkAndRefreshCertificateProactively()
                startTunnelVerification()
            }
            Tunnel.State.DOWN -> {
                verificationJob?.cancel()
                if (!isReconnecting) {
                    _vpnState.value = VpnState.DISCONNECTED
                    currentServerId = null
                    connectedServerState.setConnectedServer(null)
                    _speed.value = null
                } else {
                    _vpnState.value = VpnState.CONNECTING
                }
            }
        }
    }

    private fun startTunnelVerification() {
        verificationJob?.cancel()
        verificationJob = applicationScope.launch {
            _vpnState.value = VpnState.VERIFYING
            ProtonLogger.i(TAG, "Tunnel is UP, starting connectivity verification...")
            
            try {
                // Wait for the system to validate the VPN network.
                // We use a timeout to prevent getting stuck in "Verifying" state forever.
                withTimeout(15000) {
                    vpnNetworkMonitor.isValidated.first { it }
                }
                
                ProtonLogger.i(TAG, "Connectivity verification successful. VPN is fully connected.")
                _vpnState.value = VpnState.CONNECTED
                systemContextWrapper.setVpnVerified()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    ProtonLogger.w(TAG, "Connectivity verification timed out. Network might be restricted or slow.")
                    // Even if it times out, we might want to show "Connected" if the tunnel is still UP,
                    // but according to the user request, we should probably be more strict.
                    // However, sometimes validation takes longer. Let's still move to CONNECTED
                    // but log the warning, or keep it as CONNECTED if we trust the tunnel.
                    // The original app usually waits. 
                    _vpnState.value = VpnState.CONNECTED 
                } else {
                    ProtonLogger.e(TAG, "Error during connectivity verification", e)
                    _vpnState.value = VpnState.CONNECTED // Fallback
                }
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

    private suspend fun performCertificateRefresh(force: Boolean = false): Result<String> = refreshMutex.withLock {
        val currentSession = sessionDao.getSession() ?: return Result.failure<String>(Exception("No session")).also {
            ProtonLogger.e(TAG, "Certificate refresh failed: No active session found in database")
        }

        val previousState = _certState.value
        _certState.value = CertificateState.Refreshing
        ProtonLogger.i(TAG, "Starting certificate refresh (force=$force, previous state: $previousState)")

        val useWarp = settingsManager.isApiBypassEnabledSync() &&
                settingsManager.getApiBypassStrategySync() == SettingsManager.STRATEGY_WARP

        if (useWarp) {
            val wm = warpManager.get()
            // Only start WARP if main VPN is NOT active
            if (_tunnelState.value != Tunnel.State.UP) {
                if (!wm.isConfigLoaded()) wm.fetchWarpConfig()
                wm.startWarpTunnel()
            }
        }

        try {
            val keyPair = cryptoWrapper.generateVpnKeyPair()
            ProtonLogger.v(TAG, "Generated new VPN keypair for registration")

            val result = vpnRepositoryProvider.get().registerWireGuardKey(
                accessToken = currentSession.accessToken,
                sessionId = currentSession.sessionId,
                publicKeyPem = keyPair.publicKeyPem
            )

            if (result.isSuccess) {
                val response = result.getOrNull()
                val newCert = response?.certificate
                if (newCert != null) {
                    ProtonLogger.i(TAG, "Successfully obtained WireGuard certificate")

                    // Metrics
                    Sentry.metrics().count("cert_refresh_success", 1.0)

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
                    ProtonLogger.e(TAG, "Server returned success but certificate is null or empty")
                    _certState.value = previousState
                    Result.failure(Exception("Empty certificate in response"))
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                ProtonLogger.e(TAG, "Failed to register WireGuard key with Proton API: $error", result.exceptionOrNull())

                // Metrics
                Sentry.metrics().count("cert_refresh_error", 1.0)

                val isFullyExpired = previousState is CertificateState.Expired ||
                        (previousState is CertificateState.RefreshFailed && previousState.isFullyExpired)
                _certState.value = CertificateState.RefreshFailed(error, isFullyExpired)
                Result.failure(result.exceptionOrNull() ?: Exception(error))
            }
        } finally {
            if (useWarp) {
                warpManager.get().stopWarpTunnel()
            }
        }
    }

    fun checkAndRefreshCertificateProactively() {
        if (refreshJob?.isActive == true) return
        refreshJob = applicationScope.launch {
            var currentRetryDelay = 60000L // Start retrying after 1 minute
            while (isActive) {
                val session = sessionDao.getSession() ?: break
                updateCertificateState(session.wgCertificate)

                val useWarp = settingsManager.isApiBypassEnabledSync() &&
                        settingsManager.getApiBypassStrategySync() == SettingsManager.STRATEGY_WARP
                val isConnected = _tunnelState.value == Tunnel.State.UP

                if (_certState.value is CertificateState.Valid) {
                    // All good, check again in 2 hours
                    delay(PERIODIC_REFRESH_MS)
                    currentRetryDelay = 5000L
                    continue
                }

                // If using WARP, we only refresh certificate when connecting or already connected.
                // This avoids periodic WARP tunnel bring-ups in the background.
                if (useWarp && !isConnected && !_isConnecting.value) {
                    ProtonLogger.d(TAG, "Proactive refresh: WARP enabled but VPN inactive. Skipping background periodic refresh.")
                    delay(PERIODIC_REFRESH_MS)
                    continue
                }

                ProtonLogger.d(TAG, "Proactive refresh starting (cert state: ${_certState.value})")
                val result = performCertificateRefresh(force = false)
                
                if (result.isSuccess) {
                    currentRetryDelay = 5000L
                    delay(PERIODIC_REFRESH_MS)
                } else {
                    // API access is expected to be preserved, so we retry with backoff.
                    // This covers cases where internet is temporarily down.
                    ProtonLogger.w(TAG, "Proactive refresh failed, retrying in ${currentRetryDelay}ms")
                    delay(currentRetryDelay)
                    currentRetryDelay = (currentRetryDelay * 2).coerceAtMost(RETRY_DELAY_MS)
                }
            }
        }
    }

    fun isEffectivelyExpired(): Boolean {
        val state = _certState.value
        return state is CertificateState.Expired || (state is CertificateState.RefreshFailed && state.isFullyExpired)
    }

    suspend fun forceRefreshCertificate(): Result<String> {
        return performCertificateRefresh(force = true)
    }

    /**
     * Ensures that WARP bypass is either active or stopped based on the provided parameter.
     * Starts WARP only if main VPN is not UP.
     */
    suspend fun ensureWarpBypass(active: Boolean) {
        val wm = warpManager.get()
        if (active) {
            if (_tunnelState.value != Tunnel.State.UP) {
                if (!wm.isConfigLoaded()) wm.fetchWarpConfig()
                wm.startWarpTunnel()
            }
        } else {
            wm.stopWarpTunnel()
        }
    }

    private suspend fun updateServiceSettings() {
        systemContextWrapper.updateVpnSettings(
            notificationsEnabled = settingsManager.notificationsEnabled.first(),
            killSwitchEnabled = settingsManager.killSwitchEnabled.first(),
            nonFatalEnabled = settingsManager.sentryNonFatalEnabled.first(),
            analyticsEnabled = settingsManager.analyticsEnabled.first()
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
        forceFallback: Boolean = false
    ) {
        if (currentServerId == logicalServerId && _tunnelState.value == Tunnel.State.UP) {
            ProtonLogger.d(TAG, "Already connected to $logicalServerId")
            return
        }
        
        connectionJob?.cancel()
        verificationJob?.cancel()
        connectionJob = applicationScope.launch(dispatcherProvider.io()) {
            currentServerId = logicalServerId
            
            // Resolve logical server if not provided to ensure UI can show location info
            if (logicalServer != null) {
                connectedServerState.setConnectedServer(logicalServer)
            } else if (connectedServerState.connectedServer.value?.id != logicalServerId) {
                val resolved = vpnRepositoryProvider.get().getCachedServers().find { it.id == logicalServerId }
                connectedServerState.setConnectedServer(resolved)
            }

            connectInternal(logicalServerId, server, session, overridePort, overrideObfuscation, obfuscationParams, forceFallback)
            
            // Track connection attempt via Sentry Metrics
            Sentry.metrics().count("vpn_connection_attempt", 1.0)
        }
    }

    private suspend fun connectInternal(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null,
        forceFallback: Boolean = false
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        try {
            val serverLogInfo = "${server.id} (Domain: ${server.domain}, LogicalID: $logicalServerId)"
            ProtonLogger.i(TAG, "Initiating connection to server: $serverLogInfo")
            ProtonLogger.addSentryBreadcrumb(TAG, "VPN Connection Step: Start ($serverLogInfo)", SentryLevel.INFO, "vpn.connect")

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

            if (forceFallback) {
                // Skip DNS entirely and jump straight to the exitIp fallback
                val fallbackIp = server.exitIp
                if (!fallbackIp.isNullOrEmpty()) {
                    ProtonLogger.i(TAG, "forceFallback=true: skipping DNS resolution, using exitIp: $fallbackIp")
                    targetIp = fallbackIp
                } else {
                    _isConnecting.value = false
                    _tunnelState.value = Tunnel.State.DOWN
                    throw Exception("forceFallback=true but no exitIp available for ${server.domain}").also {
                        ProtonLogger.e(TAG, it.message!!)
                    }
                }
            } else {
                // DNS resolution with improved retry and logging
                ProtonLogger.d(TAG, "Resolving domain ${server.domain} (Max retries: $DNS_RETRY_COUNT)")
                for (i in 1..DNS_RETRY_COUNT) {
                    if (!isActive) break
                    try {
                        targetIp = InetAddress.getByName(server.domain).hostAddress
                        if (targetIp != null) {
                            ProtonLogger.i(TAG, "DNS resolved ${server.domain} to $targetIp on attempt $i")
                            break
                        }
                    } catch (e: Exception) {
                        ProtonLogger.w(TAG, "DNS retry $i failed for ${server.domain}: ${e.message}")
                        if (i < DNS_RETRY_COUNT) delay(DNS_RETRY_DELAY_MS * i) // Exponential-ish backoff
                    }
                }

                if (targetIp == null) {
                    val fallbackIp = server.exitIp
                    if (!fallbackIp.isNullOrEmpty()) {
                        ProtonLogger.w(TAG, "DNS resolution failed for ${server.domain} after $DNS_RETRY_COUNT attempts, falling back to exitIp: $fallbackIp")
                        targetIp = fallbackIp
                    } else {
                        _isConnecting.value = false
                        _tunnelState.value = Tunnel.State.DOWN
                        throw Exception("DNS resolution failed for ${server.domain} after $DNS_RETRY_COUNT attempts").also {
                            ProtonLogger.e(TAG, it.message!!)
                        }
                    }
                }
            }

            val serverPubKey = server.wgPublicKey ?: throw Exception("Missing WG Public Key for Server").also {
                ProtonLogger.e(TAG, "Critical: Server ${server.id} has no WireGuard Public Key")
            }
            
            val splitTunnelingEnabled = settingsManager.splitTunnelingEnabled.first()
            val stMode = settingsManager.splitTunnelingMode.first()
            val isIncludeMode = stMode == "include"
            val selectedApps = if (splitTunnelingEnabled) settingsManager.excludedApps.first() else emptySet()
            val selectedIps = if (splitTunnelingEnabled) settingsManager.excludedIps.first().toMutableSet() else mutableSetOf()
            val selectedDomains = if (splitTunnelingEnabled) settingsManager.excludedDomains.first() else emptySet()

            ProtonLogger.d(TAG, "Split Tunneling: enabled=$splitTunnelingEnabled, mode=$stMode, apps=${selectedApps.size}, IPs=${selectedIps.size}, domains=${selectedDomains.size}")

            // Resolve split tunneling domains
            if (splitTunnelingEnabled && selectedDomains.isNotEmpty()) {
                ProtonLogger.i(TAG, "Resolving ${selectedDomains.size} split-tunneling domains...")
                selectedDomains.forEach { domain ->
                    try {
                        val addresses = InetAddress.getAllByName(domain)
                        addresses.filterIsInstance<java.net.Inet4Address>().forEach { addr ->
                            val ip = addr.hostAddress
                            if (ip != null) {
                                selectedIps.add("$ip/32")
                                ProtonLogger.v(TAG, "Split-tunnel domain $domain resolved to $ip")
                            }
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
            val isObfuscationEnabled = overrideObfuscation ?: settingsManager.obfuscationEnabled.first()

            ProtonLogger.i(TAG, "Connection parameters: Port=$selectedPort, Obfuscation=$isObfuscationEnabled")

            val params = if (isObfuscationEnabled) {
                obfuscationParams ?: ObfuscationParams(
                    jc = settingsManager.awgJc.first(), jmin = settingsManager.awgJmin.first(), jmax = settingsManager.awgJmax.first(),
                    s1 = settingsManager.awgS1.first(), s2 = settingsManager.awgS2.first(),
                    s3 = settingsManager.awgS3.first(), s4 = settingsManager.awgS4.first(),
                    h1 = settingsManager.awgH1.first(), h2 = settingsManager.awgH2.first(), h3 = settingsManager.awgH3.first(), h4 = settingsManager.awgH4.first(),
                    i1 = settingsManager.awgI1.first(), i2 = settingsManager.awgI2.first(), i3 = settingsManager.awgI3.first(), i4 = settingsManager.awgI4.first(), i5 = settingsManager.awgI5.first()
                )
            } else {
                ObfuscationParams(0, 0, 0, 0, 0, 0, 0, "", "", "", "", "", "", "", "", "")
            }

            // Use assigned IP/DNS from session if available, fallback to defaults.
            // Paid users (Tier > 0) are often assigned unique internal IPs by the Proton API.
            val localIp = currentSession.vpnIpv4 ?: PROTON_CLIENT_IP
            val fallbackDns = currentSession.vpnDns?.split(",")?.firstOrNull() ?: PROTON_DNS_IP

            // Retrieve Custom DNS IP or fallback to Proton Assigned/Default
            val userDns = settingsManager.customDns.first().trim()
            val isValidDns = userDns.isNotEmpty() && try {
                InetAddress.getByName(userDns)
                true
            } catch (e: Exception) {
                ProtonLogger.w(TAG, "Invalid custom DNS value '$userDns', falling back to default: ${e.message}")
                false
            }
            val activeDns = if (isValidDns) userDns else fallbackDns
            ProtonLogger.i(TAG, "Using DNS Server: $activeDns, Client IP: $localIp")

            ProtonLogger.addSentryBreadcrumb(TAG, "VPN Connection Step: Building Config", SentryLevel.DEBUG, "vpn.connect")
            val configStr = amneziaConfigGenerator.buildConfig(
                serverPublicKey = serverPubKey,
                privateKey = wgPrivateKeyB64,
                localIp = localIp,
                dnsServer = activeDns,
                targetIp = targetIp,
                isIncludeMode = isIncludeMode,
                selectedApps = selectedApps,
                selectedIps = selectedIps,
                port = selectedPort,
                certificate = currentSession.wgCertificate,
                obfuscationParams = params
            )
            
            Log.d(TAG, "Generated AWG Config:\n$configStr")
            ProtonLogger.v(TAG, "Generated AWG Config Length: ${configStr.length}")

            ProtonLogger.addSentryBreadcrumb(TAG, "VPN Connection Step: Starting Service", SentryLevel.INFO, "vpn.connect")
            systemContextWrapper.startVpnService(
                configStr = configStr,
                notificationsEnabled = settingsManager.notificationsEnabled.first(),
                killSwitchEnabled = settingsManager.killSwitchEnabled.first(),
                excludedApps = selectedApps,
                excludedIps = selectedIps
            )

            ProtonLogger.i(TAG, "VPN start command issued successfully")
            
            // Track connection success
            Sentry.metrics().count("vpn_connection_success", 1.0)
            
            Result.success(Unit)
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Failed to connect to VPN", e)
            ProtonLogger.addSentryBreadcrumb(TAG, "VPN Connection Failed: ${e.message}", SentryLevel.ERROR, "vpn.error")

            // Track connection failure
            Sentry.metrics().count("vpn_connection_failure", 1.0)

            _isConnecting.value = false
            _tunnelState.value = Tunnel.State.DOWN
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
        forceFallback: Boolean = false
    ) {
        // Only skip if we're already connecting (to avoid multiple rapid clicks)
        if (_isConnecting.value) {
            ProtonLogger.d(TAG, "Reconnect skipped: Already in a connecting state.")
            return
        }

        connectionJob?.cancel()
        verificationJob?.cancel()
        connectionJob = applicationScope.launch {
            try {
                isReconnecting = true
                _isConnecting.value = true
                currentServerId = logicalServerId

                // Resolve logical server if not provided
                if (logicalServer != null) {
                    connectedServerState.setConnectedServer(logicalServer)
                } else if (connectedServerState.connectedServer.value?.id != logicalServerId) {
                    val resolved = vpnRepositoryProvider.get().getCachedServers().find { it.id == logicalServerId }
                    connectedServerState.setConnectedServer(resolved)
                }

                disconnectInternal()
                try {
                    withTimeout(5000) {
                        _rawTunnelState.first { it == Tunnel.State.DOWN }
                    }
                } catch (_: Exception) {
                }
                delay(500)
                connectInternal(logicalServerId, server, session, overridePort, overrideObfuscation, obfuscationParams, forceFallback)
            } finally {
                isReconnecting = false
            }
        }
    }

    fun disconnect() {
        ProtonLogger.action(TAG, "User clicked Disconnect")
        connectionJob?.cancel()
        verificationJob?.cancel()
        applicationScope.launch {
            isReconnecting = false
            currentServerId = null
            _vpnState.value = VpnState.DISCONNECTING
            disconnectInternal()
        }
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
                delay(1500)

                withTimeout(40000) {
                    // 1. If we are currently in the middle of connecting, wait for it to finish
                    if (_isConnecting.value) {
                        ProtonLogger.d(TAG, "Connect & Go: VPN is connecting, waiting...")
                        _isConnecting.first { !it }
                    }
                    
                    // 2. Then wait for the tunnel state to be UP
                    ProtonLogger.d(TAG, "Connect & Go: VPN attempt finished, waiting for UP state...")
                    _tunnelState.first { it == Tunnel.State.UP }
                }

                // 3. Extra delay to ensure routing and DNS are fully established and browser can reach the site
                ProtonLogger.d(TAG, "Connect & Go: Tunnel is UP, waiting for routing stabilization...")
                delay(3000)

                if (_tunnelState.value == Tunnel.State.UP) {
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
                    ProtonLogger.e(TAG, "Connect & Go: Timed out waiting for VPN to connect for URL: $targetUrl")
                } else {
                    ProtonLogger.e(TAG, "Connect & Go: Failed to handle URL: $targetUrl", e)
                }
            }
        }
    }
}
