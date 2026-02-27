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
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.di.ApplicationScope
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmneziaVpnManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AmneziaVpnManager"
        private const val PROTON_CLIENT_IP = "10.2.0.2"
        private const val PROTON_DNS_IP = "10.2.0.1"
        private const val DNS_RETRY_COUNT = 5
        private const val DNS_RETRY_DELAY_MS = 1000L
    }

    private val _tunnelState = MutableStateFlow(Tunnel.State.DOWN)
    val tunnelState: StateFlow<Tunnel.State> = _tunnelState

    // Track the raw internal state separately from the UI state to handle reconnects gracefully
    private val _rawTunnelState = MutableStateFlow(Tunnel.State.DOWN)
    private var isReconnecting = false
    private var connectionJob: Job? = null

    init {
        // Listen for state changes coming from the isolated :vpn process
        val filter = IntentFilter(ProtonVpnService.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val stateStr = intent?.getStringExtra(ProtonVpnService.EXTRA_STATE)
                stateStr?.let {
                    val newState = Tunnel.State.valueOf(it)
                    Log.d(TAG, "Syncing state from :vpn process -> $newState (isReconnecting=$isReconnecting)")

                    _rawTunnelState.value = newState

                    if (isReconnecting && newState == Tunnel.State.DOWN) {
                        // Suppress the DOWN state for the UI while we are actively reconnecting
                        Log.d(TAG, "Suppressing DOWN state for UI during reconnect")
                    } else {
                        _tunnelState.value = newState
                    }
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Connect to a server. If already connected or connecting, it cancels the previous job.
     */
    fun connect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity
    ) {
        connectionJob?.cancel()
        connectionJob = applicationScope.launch {
            connectInternal(logicalServerId, server, session)
        }
    }

    private suspend fun connectInternal(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting AWG connection to ${server.domain}")
            _tunnelState.value = Tunnel.State.TOGGLE

            // 1. Retrieve the pre-generated offline private key
            val wgPrivateKeyB64 = session.wgPrivateKey ?: throw Exception("Offline VPN certificate missing!")

            // 2. Set internal IPs
            val localIp = PROTON_CLIENT_IP
            val dnsServer = PROTON_DNS_IP

            // 3. Resolve the target Node IP with retries
            var targetIp: String? = null
            var lastError: Exception? = null

            for (i in 1..DNS_RETRY_COUNT) {
                try {
                    targetIp = InetAddress.getByName(server.domain).hostAddress
                    if (targetIp != null) break
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "DNS resolution attempt $i failed for ${server.domain}: ${e.message}")
                    if (i < DNS_RETRY_COUNT) delay(DNS_RETRY_DELAY_MS)
                }
            }

            if (targetIp == null) {
                Log.e(TAG, "Failed to resolve domain after $DNS_RETRY_COUNT attempts: ${server.domain}")
                _tunnelState.value = Tunnel.State.DOWN
                throw lastError ?: Exception("DNS resolution failed")
            }

            Log.d(TAG, "Resolved ${server.domain} to $targetIp")

            // 4. Use the static Server Public Key from the server list
            val serverPubKey = server.wgPublicKey ?: throw Exception("Missing WG Public Key for Server")

            // 5. Get split tunneling configuration
            val splitTunnelingEnabled = settingsManager.splitTunnelingEnabled.first()
            val excludedApps = if (splitTunnelingEnabled) settingsManager.excludedApps.first() else emptySet()
            val excludedIps = if (splitTunnelingEnabled) settingsManager.excludedIps.first() else emptySet()

            // 6. Build Config
            val config = buildAwgConfig(
                serverPublicKey = serverPubKey,
                privateKey = wgPrivateKeyB64,
                localIp = localIp,
                dnsServer = dnsServer,
                targetIp = targetIp,
                excludedApps = excludedApps,
                excludedIps = excludedIps,
                port = settingsManager.vpnPort.first(),
                jc = settingsManager.awgJc.first(),
                jmin = settingsManager.awgJmin.first(),
                jmax = settingsManager.awgJmax.first(),
                s1 = settingsManager.awgS1.first(),
                s2 = settingsManager.awgS2.first(),
                h1 = settingsManager.awgH1.first(),
                h2 = settingsManager.awgH2.first(),
                h3 = settingsManager.awgH3.first(),
                h4 = settingsManager.awgH4.first(),
                i1 = settingsManager.awgI1.first()
            )
            val configStr = config.toAwgQuickString()

            // 7. Send config to isolated VPN process
            val intent = Intent(context, ProtonVpnService::class.java).apply {
                action = ProtonVpnService.ACTION_CONNECT
                putExtra(ProtonVpnService.EXTRA_CONFIG, configStr)
                putStringArrayListExtra(ProtonVpnService.EXTRA_EXCLUDED_APPS, ArrayList(excludedApps))
                putStringArrayListExtra(ProtonVpnService.EXTRA_EXCLUDED_IPS, ArrayList(excludedIps))
            }
            context.startService(intent)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _tunnelState.value = Tunnel.State.DOWN
            Result.failure(e)
        }
    }

    /**
     * Performs a clean reconnect: disconnects, waits for DOWN state, and then connects.
     */
    fun reconnect(
        logicalServerId: String,
        server: PhysicalServer,
        session: SessionEntity
    ) {
        connectionJob?.cancel()
        connectionJob = applicationScope.launch {
            Log.d(TAG, "Initiating clean reconnect...")
            isReconnecting = true
            _tunnelState.value = Tunnel.State.TOGGLE // Show loading immediately in UI

            disconnectInternal()

            try {
                withTimeout(5000) {
                    _rawTunnelState.first { it == Tunnel.State.DOWN }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Timeout waiting for raw tunnel DOWN during reconnect", e)
            }

            delay(500)
            isReconnecting = false // Re-enable normal UI state updates
            connectInternal(logicalServerId, server, session)
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        applicationScope.launch {
            isReconnecting = false // Make sure we don't suppress the disconnect event
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
        jc: Int = 3,
        jmin: Int = 1,
        jmax: Int = 3,
        s1: Int = 0,
        s2: Int = 0,
        h1: String = "1",
        h2: String = "2",
        h3: String = "3",
        h4: String = "4",
        i1: String = ""
    ): Config {
        val allowedIpsList = if (excludedIps.isEmpty()) {
            listOf("0.0.0.0/0")
        } else {
            IpSubnetCalculator.complementOfExcluded(excludedIps)
        }

        val peer = Peer.Builder()
            .parsePublicKey(serverPublicKey)
            .parseEndpoint("$targetIp:$port")
            .apply {
                if (allowedIpsList.isEmpty()) {
                    parseAllowedIPs("0.0.0.0/0")
                } else {
                    allowedIpsList.forEach { ip ->
                        parseAllowedIPs(ip)
                    }
                }
            }
            .setPersistentKeepalive(60)
            .build()

        val ifaceBuilder: Interface.Builder = Interface.Builder()
        ifaceBuilder.parsePrivateKey(privateKey)
        ifaceBuilder.parseAddresses("$localIp/32")
        ifaceBuilder.parseDnsServers(dnsServer)
        ifaceBuilder.setMtu(1280)
        ifaceBuilder.setJunkPacketCount(jc)
        ifaceBuilder.setJunkPacketMinSize(jmin)
        ifaceBuilder.setJunkPacketMaxSize(jmax)
        ifaceBuilder.setInitPacketJunkSize(s1)
        ifaceBuilder.setResponsePacketJunkSize(s2)
        ifaceBuilder.setInitPacketMagicHeader(h1)
        ifaceBuilder.setResponsePacketMagicHeader(h2)
        ifaceBuilder.setUnderloadPacketMagicHeader(h3)
        ifaceBuilder.setTransportPacketMagicHeader(h4)

        if (i1.isNotEmpty()) {
            ifaceBuilder.parseSpecialJunkI1(i1)
        }

        if (excludedApps.isNotEmpty()) {
            ifaceBuilder.parseExcludedApplications(excludedApps.joinToString(","))
        }

        val iface = ifaceBuilder.build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }
}
