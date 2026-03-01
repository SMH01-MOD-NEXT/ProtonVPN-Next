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
import android.util.Log
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import org.amnezia.awg.config.Interface
import org.amnezia.awg.config.Peer
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.di.ApplicationScope
import java.io.ByteArrayInputStream
import java.net.InetAddress
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
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AmneziaVpnManager"
        private const val PROTON_CLIENT_IP = "10.2.0.2"
        private const val PROTON_DNS_IP = "10.2.0.1"
        private const val DNS_RETRY_COUNT = 5
        private const val DNS_RETRY_DELAY_MS = 1000L
        private const val STATE_CONNECTING = "CONNECTING"
        
        private const val REFRESH_THRESHOLD_MS = 6 * 3600 * 1000L // 6 hours
        private const val RETRY_DELAY_MS = 15 * 60 * 1000L // 15 minutes
        private const val PERIODIC_REFRESH_MS = 2 * 3600 * 1000L // 2 hours
    }

    sealed class CertificateState {
        data object Valid : CertificateState()
        data class ExpiringSoon(val hoursRemaining: Int) : CertificateState()
        data object Expired : CertificateState()
        data class RefreshFailed(val error: String, val isFullyExpired: Boolean) : CertificateState()
        data object Refreshing : CertificateState()
    }

    private val _certState = MutableStateFlow<CertificateState>(CertificateState.Valid)
    val certState: StateFlow<CertificateState> = _certState.asStateFlow()

    data class ObfuscationParams(
        val jc: Int, val jmin: Int, val jmax: Int,
        val s1: Int, val s2: Int,
        val h1: String, val h2: String, val h3: String, val h4: String,
        val i1: String
    )

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _tunnelState = MutableStateFlow(Tunnel.State.DOWN)
    val tunnelState: StateFlow<Tunnel.State> = _tunnelState

    private val _rawTunnelState = MutableStateFlow(Tunnel.State.DOWN)
    private var isReconnecting = false
    private var connectionJob: Job? = null
    private var refreshJob: Job? = null
    private var scheduledRefreshJob: Job? = null

    init {
        val filter = IntentFilter(ProtonVpnService.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val stateStr = intent?.getStringExtra(ProtonVpnService.EXTRA_STATE)
                stateStr?.let {
                    if (it == STATE_CONNECTING) {
                        _isConnecting.value = true
                    } else {
                        val newState = Tunnel.State.valueOf(it)
                        _rawTunnelState.value = newState
                        _isConnecting.value = false
                        if (!(isReconnecting && newState == Tunnel.State.DOWN)) {
                            _tunnelState.value = newState
                            if (newState == Tunnel.State.UP) {
                                checkAndRefreshCertificateProactively()
                            } else if (newState == Tunnel.State.DOWN) {
                                scheduledRefreshJob?.cancel()
                            }
                        }
                    }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        applicationScope.launch { settingsManager.notificationsEnabled.collect { updateServiceSettings() } }
        applicationScope.launch { settingsManager.killSwitchEnabled.collect { updateServiceSettings() } }
        
        applicationScope.launch {
            val session = sessionDao.getSession()
            if (session != null) {
                updateCertificateState(session.wgCertificate)
                // Always try to refresh on app start if not perfectly valid
                if (_certState.value !is CertificateState.Valid) {
                    checkAndRefreshCertificateProactively()
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

    private suspend fun performCertificateRefresh(session: SessionEntity): Result<String> {
        val previousState = _certState.value
        _certState.value = CertificateState.Refreshing
        val result = vpnRepositoryProvider.get().registerWireGuardKey(
            accessToken = session.accessToken,
            sessionId = session.sessionId,
            publicKeyPem = session.wgPublicKeyPem ?: ""
        )
        return if (result.isSuccess) {
            val newCert = result.getOrNull()?.certificate
            if (newCert != null) {
                sessionDao.updateCertificate(newCert)
                updateCertificateState(newCert)
                Result.success(newCert)
            } else {
                _certState.value = previousState
                Result.failure(Exception("Empty certificate in response"))
            }
        } else {
            val error = result.exceptionOrNull()?.message ?: "Unknown error"
            val isFullyExpired = previousState is CertificateState.Expired || 
                                (previousState is CertificateState.RefreshFailed && previousState.isFullyExpired)
            _certState.value = CertificateState.RefreshFailed(error, isFullyExpired)
            Result.failure(result.exceptionOrNull() ?: Exception(error))
        }
    }

    fun checkAndRefreshCertificateProactively() {
        if (refreshJob?.isActive == true) return
        refreshJob = applicationScope.launch {
            while (true) {
                val session = sessionDao.getSession() ?: break
                updateCertificateState(session.wgCertificate)
                if (_certState.value is CertificateState.Valid) break
                val result = performCertificateRefresh(session)
                if (result.isSuccess) break
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun updateServiceSettings() {
        val intent = Intent(ProtonVpnService.ACTION_UPDATE_SETTINGS).apply {
            setPackage(context.packageName)
            putExtra(ProtonVpnService.EXTRA_NOTIFICATIONS_ENABLED, settingsManager.notificationsEnabled.first())
            putExtra(ProtonVpnService.EXTRA_KILL_SWITCH_ENABLED, settingsManager.killSwitchEnabled.first())
        }
        context.sendBroadcast(intent)
    }

    fun connect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null
    ) {
        connectionJob?.cancel()
        connectionJob = applicationScope.launch {
            connectInternal(logicalServerId, server, session, overridePort, overrideObfuscation, obfuscationParams)
        }
    }

    private suspend fun connectInternal(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isConnecting.value = true
            var currentSession = session
            updateCertificateState(currentSession.wgCertificate)

            // 1. Strict blocking for Expired certificate
            if (_certState.value is CertificateState.Expired) {
                Log.d(TAG, "Blocking connection: Certificate is fully expired.")
                while (true) {
                    val refreshResult = performCertificateRefresh(currentSession)
                    if (refreshResult.isSuccess) {
                        currentSession = currentSession.copy(wgCertificate = refreshResult.getOrNull()!!)
                        break
                    }
                    delay(5000) // Retry every 5s while blocking
                    currentSession = sessionDao.getSession() ?: throw Exception("Session lost during refresh")
                }
            }

            val wgPrivateKeyB64 = currentSession.wgPrivateKey ?: throw Exception("Offline VPN private key missing!")
            var targetIp: String? = null
            for (i in 1..DNS_RETRY_COUNT) {
                try {
                    targetIp = InetAddress.getByName(server.domain).hostAddress
                    if (targetIp != null) break
                } catch (e: Exception) {
                    if (i < DNS_RETRY_COUNT) delay(DNS_RETRY_DELAY_MS)
                }
            }

            if (targetIp == null) {
                _isConnecting.value = false
                _tunnelState.value = Tunnel.State.DOWN
                throw Exception("DNS resolution failed for ${server.domain}")
            }

            val serverPubKey = server.wgPublicKey ?: throw Exception("Missing WG Public Key for Server")
            val splitTunnelingEnabled = settingsManager.splitTunnelingEnabled.first()
            val excludedApps = if (splitTunnelingEnabled) settingsManager.excludedApps.first() else emptySet()
            val excludedIps = if (splitTunnelingEnabled) settingsManager.excludedIps.first() else emptySet()
            val selectedPort = overridePort?.takeIf { it != 0 } ?: settingsManager.vpnPort.first().let { port ->
                if (port == 0) listOf(443, 123, 1194, 51820).random() else port
            }
            val isObfuscationEnabled = overrideObfuscation ?: settingsManager.obfuscationEnabled.first()

            val params = if (isObfuscationEnabled) {
                obfuscationParams ?: ObfuscationParams(
                    jc = settingsManager.awgJc.first(), jmin = settingsManager.awgJmin.first(), jmax = settingsManager.awgJmax.first(),
                    s1 = settingsManager.awgS1.first(), s2 = settingsManager.awgS2.first(),
                    h1 = settingsManager.awgH1.first(), h2 = settingsManager.awgH2.first(), h3 = settingsManager.awgH3.first(), h4 = settingsManager.awgH4.first(),
                    i1 = settingsManager.awgI1.first()
                )
            } else {
                ObfuscationParams(0, 0, 0, 0, 0, "", "", "", "", "")
            }

            val config = buildAwgConfig(
                serverPublicKey = serverPubKey, privateKey = wgPrivateKeyB64, localIp = PROTON_CLIENT_IP, dnsServer = PROTON_DNS_IP,
                targetIp = targetIp, excludedApps = excludedApps, excludedIps = excludedIps, port = selectedPort,
                jc = params.jc, jmin = params.jmin, jmax = params.jmax, s1 = params.s1, s2 = params.s2,
                h1 = params.h1, h2 = params.h2, h3 = params.h3, h4 = params.h4, i1 = params.i1
            )

            val intent = Intent(context, ProtonVpnService::class.java).apply {
                action = ProtonVpnService.ACTION_CONNECT
                putExtra(ProtonVpnService.EXTRA_CONFIG, config.toAwgQuickString(false, false))
                putExtra(ProtonVpnService.EXTRA_NOTIFICATIONS_ENABLED, settingsManager.notificationsEnabled.first())
                putExtra(ProtonVpnService.EXTRA_KILL_SWITCH_ENABLED, settingsManager.killSwitchEnabled.first())
                putStringArrayListExtra(ProtonVpnService.EXTRA_EXCLUDED_APPS, ArrayList(excludedApps))
                putStringArrayListExtra(ProtonVpnService.EXTRA_EXCLUDED_IPS, ArrayList(excludedIps))
            }
            context.startService(intent)

            // Start periodic refresh job (3s initially, then every 2h)
            startScheduledRefresh()

            Result.success(Unit)
        } catch (e: Exception) {
            _isConnecting.value = false
            _tunnelState.value = Tunnel.State.DOWN
            Result.failure(e)
        }
    }

    private fun startScheduledRefresh() {
        scheduledRefreshJob?.cancel()
        scheduledRefreshJob = applicationScope.launch {
            // Wait for connection to be active
            _tunnelState.first { it == Tunnel.State.UP }
            
            // First refresh after 3 seconds
            delay(3000)
            Log.d(TAG, "Initial 3s post-connect refresh.")
            val sessionAfterConnect = sessionDao.getSession() ?: return@launch
            performCertificateRefresh(sessionAfterConnect)

            // Then every 2 hours without reconnecting
            while (_tunnelState.value == Tunnel.State.UP) {
                delay(PERIODIC_REFRESH_MS)
                Log.d(TAG, "Periodic 2h background refresh.")
                val sessionPeriodic = sessionDao.getSession() ?: break
                performCertificateRefresh(sessionPeriodic)
            }
        }
    }

    fun reconnect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity,
        overridePort: Int? = null,
        overrideObfuscation: Boolean? = null,
        obfuscationParams: ObfuscationParams? = null
    ) {
        connectionJob?.cancel()
        connectionJob = applicationScope.launch {
            isReconnecting = true
            _isConnecting.value = true
            disconnectInternal()
            try { withTimeout(5000) { _rawTunnelState.first { it == Tunnel.State.DOWN } } } catch (_: Exception) {}
            delay(500)
            isReconnecting = false 
            connectInternal(logicalServerId, server, session, overridePort, overrideObfuscation, obfuscationParams)
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        scheduledRefreshJob?.cancel()
        applicationScope.launch {
            isReconnecting = false
            disconnectInternal()
        }
    }

    private suspend fun disconnectInternal() = withContext(Dispatchers.IO) {
        val intent = Intent(context, ProtonVpnService::class.java).apply {
            action = ProtonVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    private fun buildAwgConfig(
        serverPublicKey: String,
        privateKey: String,
        localIp: String,
        dnsServer: String,
        targetIp: String,
        excludedApps: Set<String> = emptySet(),
        excludedIps: Set<String> = emptySet(),
        port: Int = 1194,
        jc: Int = 3, jmin: Int = 1, jmax: Int = 3,
        s1: Int = 0, s2: Int = 0,
        h1: String = "1", h2: String = "2", h3: String = "3", h4: String = "4",
        i1: String = ""
    ): Config {
        val allowedIpsList = if (excludedIps.isEmpty()) listOf("0.0.0.0/0") else IpSubnetCalculator.complementOfExcluded(excludedIps)
        val peer = Peer.Builder()
            .parsePublicKey(serverPublicKey)
            .parseEndpoint("$targetIp:$port")
            .apply {
                if (allowedIpsList.isEmpty()) parseAllowedIPs("0.0.0.0/0") else allowedIpsList.forEach { parseAllowedIPs(it) }
            }
            .setPersistentKeepalive(60)
            .build()

        val ifaceBuilder = Interface.Builder()
            .parsePrivateKey(privateKey)
            .parseAddresses("$localIp/32")
            .parseDnsServers(dnsServer)
            .setMtu(1280)
            .setJunkPacketCount(jc)
            .setJunkPacketMinSize(jmin)
            .setJunkPacketMaxSize(jmax)
            .setInitPacketJunkSize(s1)
            .setResponsePacketJunkSize(s2)
            .apply {
                if (h1.isNotEmpty()) setInitPacketMagicHeader(h1)
                if (h2.isNotEmpty()) setResponsePacketMagicHeader(h2)
                if (h3.isNotEmpty()) setUnderloadPacketMagicHeader(h3)
                if (h4.isNotEmpty()) setTransportPacketMagicHeader(h4)
            }
        
        if (i1.isNotEmpty()) ifaceBuilder.parseSpecialJunkI1(i1)
        if (excludedApps.isNotEmpty()) ifaceBuilder.parseExcludedApplications(excludedApps.joinToString(","))

        return Config.Builder().setInterface(ifaceBuilder.build()).addPeer(peer).build()
    }
}
