/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package ru.protonmod.next.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.protonmod.next.utils.ProtonLogger
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.ConsistentCopyVisibility
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks Android VPN networks independently from the default network.
 *
 * Android's NET_CAPABILITY_VALIDATED is driven by system captive-portal probes and can arrive many
 * seconds after applications already exchange traffic through a working tunnel. A verification
 * cycle therefore accepts either the system capability or a successful TCP probe explicitly bound
 * to the newly-created VPN network. Binding the socket to that Network ensures a direct underlying
 * connection cannot produce a false positive.
 */
@Singleton
class VpnNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    @ConsistentCopyVisibility
    data class VerificationCycle internal constructor(
        internal val id: Long,
        internal val baselineHandles: Set<Long>
    )

    data class ConnectionPreflight(
        val endpointIpv4: String,
        val proxyServerOverrides: Map<String, String> = emptyMap(),
    )

    data class TrackedNetwork(
        val network: Network,
        val capabilities: NetworkCapabilities? = null,
        val linkProperties: LinkProperties? = null
    ) {
        val systemValidated: Boolean
            get() = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private data class Snapshot(
        val networks: Map<Long, TrackedNetwork> = emptyMap()
    )

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val cycleIds = AtomicLong(0)
    private val snapshot = MutableStateFlow(Snapshot())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetwork(network)

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateNetwork(network, capabilities)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            updateLinkProperties(network, linkProperties)
        }

        override fun onLost(network: Network) {
            val handle = network.networkHandle
            snapshot.update { current ->
                Snapshot(current.networks - handle)
            }
        }
    }

    init {
        try {
            // Adapted from drxekus/0c7543e: Builder defaults to NOT_VPN, making
            // verification blind to the very tunnel it is supposed to observe.
            val request = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (error: Exception) {
            ProtonLogger.e(TAG, "Failed to register network callback", error)
        }
    }

    /** Returns a snapshot of all currently active networks. */
    fun getTrackedNetworks(): Collection<TrackedNetwork> = snapshot.value.networks.values

    /**
     * Resolves and validates everything that would otherwise need DNS after the TUN is created.
     * The lookup and probes are explicitly bound to a physical, non-VPN Network, so switching
     * servers cannot recursively send the next endpoint lookup through the old VPN tunnel.
     * A nullable return is kept for test doubles; the production implementation either succeeds
     * or throws a descriptive error.
     */
    suspend fun prepareUnderlyingConnection(
        endpointHost: String,
        proxyChainConfig: String? = null,
    ): ConnectionPreflight = withContext(Dispatchers.IO) {
        val network = awaitUnderlyingNetwork()
            ?: error("No usable underlying network is available")

        // The probe targets are public resolvers, and some networks block exactly those addresses
        // while still carrying VPN traffic fine. Treating a failed probe as fatal made connecting
        // impossible on such networks (ANDROID-22P), so it stays advisory: the endpoint lookup and
        // the proxy reachability check below are the checks that actually gate the connection.
        if (!probeNetwork(network)) {
            ProtonLogger.w(TAG, "Underlying network probe failed; continuing with endpoint checks")
        }

        val endpointIpv4 = resolveIpv4(network, endpointHost)
            ?: error("No IPv4 address found for $endpointHost on the underlying network")

        val proxyLinks = proxyChainConfig.orEmpty().lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        val proxyInfo = proxyLinks.map(ProxyLinkParser::inspectLink)
        val overrides = proxyInfo.mapNotNull { info ->
            if (info.server.none(Char::isLetter)) return@mapNotNull null
            val address = resolveIpv4(network, info.server)
                ?: error("No IPv4 address found for proxy ${info.server}")
            info.server to address
        }.toMap()

        // The last configured outbound is the physical-network-facing hop because each previous
        // outbound detours to the next one. Validate that TCP endpoint before starting VpnService.
        proxyInfo.lastOrNull()?.let { proxy ->
            val address = overrides[proxy.server] ?: proxy.server
            check(probeTcp(network, address, proxy.port)) {
                "Proxy endpoint ${proxy.server}:${proxy.port} is unreachable"
            }
        }

        ConnectionPreflight(endpointIpv4, overrides)
    }

    suspend fun resolveIpv4OnUnderlying(host: String): String? = withContext(Dispatchers.IO) {
        awaitUnderlyingNetwork()?.let { resolveIpv4(it, host) }
    }

    /** Call once when a fresh connection attempt enters CONNECTING. */
    fun beginVerificationCycle(): VerificationCycle {
        return VerificationCycle(
            id = cycleIds.incrementAndGet(),
            baselineHandles = snapshot.value.networks.keys
        )
    }

    /**
     * Waits until the cycle's new VPN network is actually usable.
     *
     * Android validation wins immediately when available. Otherwise a short TCP connection is made
     * through the VPN Network itself. This reflects real tunnel usability instead of waiting for
     * Android's delayed captive-portal validation. Returns false on timeout; cancellation propagates.
     */
    suspend fun awaitUsable(
        cycle: VerificationCycle,
        timeout: Duration = DEFAULT_TIMEOUT,
        retryDelay: Duration = DEFAULT_RETRY_DELAY
    ): Boolean = withTimeoutOrNull(timeout) {
        while (true) {
            val candidate = snapshot.value.networks.entries.firstOrNull { (handle, _) ->
                handle !in cycle.baselineHandles
            }

            if (candidate == null) {
                delay(retryDelay)
                continue
            }

            val handle = candidate.key
            val tracked = candidate.value
            if (tracked.systemValidated) {
                ProtonLogger.d(TAG, "VPN network system-validated for cycle ${cycle.id}")
                return@withTimeoutOrNull true
            }

            if (probeVpnNetwork(tracked.network)) {
                ProtonLogger.d(TAG, "VPN network passed active traffic probe for cycle ${cycle.id}")
                return@withTimeoutOrNull true
            }

            // The network may have disappeared or changed capabilities while the probe was running.
            if (snapshot.value.networks[handle]?.systemValidated == true) {
                ProtonLogger.d(TAG, "VPN network system-validated during probe for cycle ${cycle.id}")
                return@withTimeoutOrNull true
            }
            delay(retryDelay)
        }
        @Suppress("UNREACHABLE_CODE")
        false
    } ?: false

    private suspend fun awaitUnderlyingNetwork(): Network? = withTimeoutOrNull(UNDERLYING_TIMEOUT) {
        while (true) {
            val candidates = getTrackedNetworks().mapNotNull { tracked ->
                val capabilities = tracked.capabilities ?: return@mapNotNull null
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                ) return@mapNotNull null
                tracked.network to capabilities
            }
            candidates.firstOrNull { (_, capabilities) ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }?.first?.let { return@withTimeoutOrNull it }
            candidates.firstOrNull()?.first?.let { return@withTimeoutOrNull it }
            delay(DEFAULT_RETRY_DELAY)
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    private fun resolveIpv4(network: Network, host: String): String? = runCatching {
        network.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
    }.getOrNull()

    private fun probeNetwork(network: Network): Boolean = PROBE_TARGETS.any { target ->
        probeTcp(network, target, PROBE_PORT)
    }

    private fun probeTcp(network: Network, host: String, port: Int): Boolean = runCatching {
        network.socketFactory.createSocket().use { socket ->
            socket.connect(InetSocketAddress(host, port), PREFLIGHT_CONNECT_TIMEOUT_MS)
        }
        true
    }.getOrDefault(false)

    private suspend fun probeVpnNetwork(network: Network): Boolean = withContext(Dispatchers.IO) {
        PROBE_TARGETS.any { target ->
            runCatching {
                network.socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(target, PROBE_PORT), PROBE_CONNECT_TIMEOUT_MS)
                }
                true
            }.getOrDefault(false)
        }
    }

    private fun refreshNetwork(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val properties = connectivityManager.getLinkProperties(network)
        val handle = network.networkHandle
        snapshot.update { current ->
            Snapshot(current.networks + (handle to TrackedNetwork(network, capabilities, properties)))
        }
    }

    private fun updateNetwork(network: Network, capabilities: NetworkCapabilities) {
        val handle = network.networkHandle
        snapshot.update { current ->
            val existing = current.networks[handle]
            val updated = existing?.copy(capabilities = capabilities)
                ?: TrackedNetwork(network, capabilities = capabilities)
            if (existing == updated) current
            else Snapshot(current.networks + (handle to updated))
        }
    }

    private fun updateLinkProperties(network: Network, properties: LinkProperties) {
        val handle = network.networkHandle
        snapshot.update { current ->
            val existing = current.networks[handle]
            val updated = existing?.copy(linkProperties = properties)
                ?: TrackedNetwork(network, linkProperties = properties)
            if (existing == updated) current
            else Snapshot(current.networks + (handle to updated))
        }
    }

    private companion object {
        const val TAG = "VpnNetworkMonitor"
        val DEFAULT_TIMEOUT = 8.seconds
        val DEFAULT_RETRY_DELAY = 200.milliseconds
        const val PROBE_PORT = 443
        const val PROBE_CONNECT_TIMEOUT_MS = 750
        const val PREFLIGHT_CONNECT_TIMEOUT_MS = 1_500
        val UNDERLYING_TIMEOUT = 8.seconds
        val PROBE_TARGETS = listOf("1.1.1.1", "8.8.8.8")
    }
}
