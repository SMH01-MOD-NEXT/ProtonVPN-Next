/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.network.probe

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.repository.ServerProbeRepository
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.di.ApplicationScope
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import ru.protonmod.next.vpn.AWG_ENDPOINT_MTU
import ru.protonmod.next.vpn.AmneziaVpnManager
import ru.protonmod.next.vpn.VpnNetworkMonitor
import ru.protonmod.next.vpn.VpnTunnelState
import java.net.Inet4Address
import java.net.InetAddress
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** What a scan covers. */
sealed interface ServerScanScope {
    data object All : ServerScanScope

    data class Country(val code: String) : ServerScanScope

    data class Server(val logicalServerId: String) : ServerScanScope
}

enum class ServerScanPhase { IDLE, PREPARING, CERTIFICATE, CHECKING_NETWORK, SCANNING, CONFIRMING, FINISHED }

enum class ServerScanError {
    NOT_LOGGED_IN,
    NO_SERVERS,
    ONLY_ACTIVE_SERVER,
    NO_NETWORK,
    NETWORK_LOST,
    CERTIFICATE,
    VPN_BUSY,
    OBFUSCATION_CONFIG,
    FAILED,
}

data class ServerScanState(
    val phase: ServerScanPhase = ServerScanPhase.IDLE,
    val scope: ServerScanScope? = null,
    val total: Int = 0,
    val completed: Int = 0,
    val available: Int = 0,
    val blocked: Int = 0,
    val unknown: Int = 0,
    val skippedActive: Int = 0,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val cancelled: Boolean = false,
    val error: ServerScanError? = null,
) {
    val isRunning: Boolean
        get() = phase != ServerScanPhase.IDLE && phase != ServerScanPhase.FINISHED

    /** Probes that ended without a reply and still wait for an internet check to confirm them. */
    val awaitingConfirmation: Int
        get() = (completed - available - blocked - unknown).coerceAtLeast(0)
}

/**
 * Checks Proton servers for censorship without bringing a tunnel up.
 *
 * Every physical server gets a real WireGuard/AmneziaWG handshake over the phone's physical
 * network, built exactly like the tunnel's own packets:
 * - a decrypted handshake response proves that the server is reachable (it cannot be forged);
 * - silence counts as "blocked" only while independent internet checks keep succeeding;
 * - everything else (busy servers, junk replies, local errors) stays "could not verify".
 */
@Singleton
class ServerScanManager @Inject constructor(
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao,
    private val settingsManager: SettingsManager,
    private val amneziaVpnManager: AmneziaVpnManager,
    private val networkMonitor: VpnNetworkMonitor,
    private val connectedServerState: ConnectedServerState,
    private val probeRepository: ServerProbeRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val _state = MutableStateFlow(ServerScanState())
    val state: StateFlow<ServerScanState> = _state.asStateFlow()

    private val random = SecureRandom()
    private val timestamps = HandshakeTimestampSource()
    private val lock = Any()
    private var scanJob: Job? = null

    /** Starts a scan unless one is running. The scan keeps going when its screen is closed. */
    fun start(scope: ServerScanScope): Boolean = synchronized(lock) {
        if (scanJob?.isActive == true) return false
        _state.value = ServerScanState(
            phase = ServerScanPhase.PREPARING,
            scope = scope,
            startedAt = System.currentTimeMillis(),
        )
        scanJob = appScope.launch(dispatcherProvider.io()) { runScan(scope) }
        true
    }

    fun stop() {
        synchronized(lock) { scanJob }?.cancel()
    }

    private suspend fun runScan(scope: ServerScanScope) {
        val error = try {
            scan(scope)
        } catch (cancelled: CancellationException) {
            finish(error = null, cancelled = true)
            throw cancelled
        } catch (failure: Exception) {
            ProtonLogger.e(TAG, "Server scan failed", failure)
            ServerScanError.FAILED
        }
        finish(error, cancelled = false)
    }

    private fun finish(error: ServerScanError?, cancelled: Boolean) {
        _state.update {
            it.copy(
                phase = ServerScanPhase.FINISHED,
                finishedAt = System.currentTimeMillis(),
                error = error,
                cancelled = cancelled,
            )
        }
    }

    private suspend fun scan(scope: ServerScanScope): ServerScanError? {
        val vpnState = amneziaVpnManager.vpnState.value
        if (vpnState in BUSY_STATES) return ServerScanError.VPN_BUSY
        val tunnelActive = vpnState == AmneziaVpnManager.VpnState.CONNECTED ||
            amneziaVpnManager.tunnelState.value == VpnTunnelState.UP

        var session = sessionDao.getSession() ?: return ServerScanError.NOT_LOGGED_IN
        if (!session.hasUsableCertificate()) {
            // Security: a refresh may rotate the account key, which would break a running tunnel.
            if (tunnelActive) return ServerScanError.CERTIFICATE
            setPhase(ServerScanPhase.CERTIFICATE)
            if (amneziaVpnManager.forceRefreshCertificate().isFailure) return ServerScanError.CERTIFICATE
            session = sessionDao.getSession() ?: return ServerScanError.NOT_LOGGED_IN
            if (!session.hasUsableCertificate()) return ServerScanError.CERTIFICATE
            // Servers learn about a new key asynchronously.
            delay(CERTIFICATE_ACTIVATION_DELAY_MS)
        }
        val privateKey = decodeKey(session.wgPrivateKey) ?: return ServerScanError.CERTIFICATE

        setPhase(ServerScanPhase.PREPARING)
        val preferences = settingsManager.serverScanPreferences.first()
        val obfuscation = try {
            resolveObfuscation(preferences.obfuscationMode)
        } catch (error: AwgConfigException) {
            ProtonLogger.w(TAG, "Invalid AmneziaWG parameters: " + error.message)
            return ServerScanError.OBFUSCATION_CONFIG
        }
        val selected = ServerScanEngine.selectTargets(loadServers(session), scope, session.userTier)
        if (selected.isEmpty()) return ServerScanError.NO_SERVERS

        val activeServer = if (tunnelActive) connectedServerState.connectedServer.value else null
        val activeIds = activeServer?.servers.orEmpty().mapTo(HashSet()) { it.id }
        val targets = selected.filter { it.physical.id !in activeIds }
        _state.update { it.copy(total = targets.size, skippedActive = selected.size - targets.size) }
        if (targets.isEmpty()) return ServerScanError.ONLY_ACTIVE_SERVER

        setPhase(ServerScanPhase.CHECKING_NETWORK)
        val network = networkMonitor.underlyingNetwork() ?: return ServerScanError.NO_NETWORK
        val probeNetwork = AndroidProbeNetwork(network, random, ioDispatcher = dispatcherProvider.io())
        if (!probeNetwork.isInternetReachable(BASELINE_TIMEOUT_MS)) return ServerScanError.NO_NETWORK
        // Never probe the server carrying the live tunnel: an accepted handshake moves its endpoint.
        val activeAddresses: Set<InetAddress> = activeServer?.servers.orEmpty().flatMap { physical ->
            listOfNotNull(probeNetwork.resolveIpv4(physical.domain), parseIpv4Literal(physical.exitIp))
        }.toSet()

        val config = WireGuardProber.Config(
            ports = resolvePorts(preferences),
            timeoutMs = preferences.timeoutMs,
            attempts = preferences.attempts,
            obfuscation = obfuscation,
            mtu = AWG_ENDPOINT_MTU,
            dataCheck = dataCheckFor(session, preferences),
        )
        val prober = WireGuardProber(privateKey, random, timestamps, probeNetwork::openDatagramSocket)
        privateKey.fill(0)
        return runTargets(targets, prober, probeNetwork, config, preferences.threads, preferences.tcpCheck, activeAddresses)
    }

    private suspend fun runTargets(
        targets: List<ScanTarget>,
        prober: WireGuardProber,
        network: AndroidProbeNetwork,
        config: WireGuardProber.Config,
        threads: Int,
        tcpCheck: Boolean,
        activeAddresses: Set<InetAddress>,
    ): ServerScanError? {
        setPhase(ServerScanPhase.SCANNING)
        val gate = BaselineGate<ServerProbeResult>()
        val writes = Channel<ServerProbeResult>(Channel.UNLIMITED)
        // The writer lives in the application scope so that results survive a stopped scan.
        val writer = appScope.launch(dispatcherProvider.io()) { persist(writes) }
        var error: ServerScanError? = null
        try {
            coroutineScope {
                val monitor = launch {
                    var failures = 0
                    while (true) {
                        delay(BASELINE_INTERVAL_MS)
                        failures = if (checkBaseline(network, gate, writes)) 0 else failures + 1
                        if (failures >= MAX_BASELINE_FAILURES) throw NetworkLostException()
                    }
                }
                ServerScanEngine.runAll(targets, threads, dispatcherProvider.io().limitedParallelism(threads)) { target ->
                    handle(probeTarget(target, prober, network, config, tcpCheck, activeAddresses), gate, writes)
                }
                monitor.cancel()
            }
            setPhase(ServerScanPhase.CONFIRMING)
            if (!checkBaseline(network, gate, writes)) error = ServerScanError.NETWORK_LOST
        } catch (_: NetworkLostException) {
            error = ServerScanError.NETWORK_LOST
        } finally {
            withContext(NonCancellable) {
                release(BaselineGate.Release(emptyList(), gate.drain()), writes)
                writes.close()
                writer.join()
            }
        }
        return error
    }

    private suspend fun probeTarget(
        target: ScanTarget,
        prober: WireGuardProber,
        network: AndroidProbeNetwork,
        config: WireGuardProber.Config,
        tcpCheck: Boolean,
        activeAddresses: Set<InetAddress>,
    ): TargetOutcome {
        val startedAt = System.nanoTime()
        val physical = target.physical
        val publicKey = decodeKey(physical.wgPublicKey)
            ?: return TargetOutcome.Final(result(target, ServerAvailability.UNKNOWN, ServerProbeDetail.NO_PUBLIC_KEY))
        val address = network.resolveIpv4(physical.domain) ?: parseIpv4Literal(physical.exitIp)
            ?: return TargetOutcome.Final(result(target, ServerAvailability.UNKNOWN, ServerProbeDetail.DNS_FAILED))
        if (address in activeAddresses) return TargetOutcome.Skipped
        val endpoint = address.hostAddress
        val outcome = prober.probe(publicKey, address, config)
        val verdict = ServerScanEngine.classify(outcome) {
            if (tcpCheck) network.tcpReachability(address, TCP_CHECK_PORT, config.timeoutMs) else null
        }
        val verified = outcome as? WireGuardProber.Outcome.Verified
        val probeResult = result(
            target,
            verdict.availability,
            verdict.detail,
            endpoint,
            verified?.ports.orEmpty(),
            verified?.latencyMs,
        )
        return if (verdict.needsBaseline) TargetOutcome.Held(startedAt, probeResult) else TargetOutcome.Final(probeResult)
    }

    private fun handle(outcome: TargetOutcome, gate: BaselineGate<ServerProbeResult>, writes: Channel<ServerProbeResult>) {
        when (outcome) {
            is TargetOutcome.Final -> {
                count(outcome.result, writes)
                _state.update { it.copy(completed = it.completed + 1) }
            }

            is TargetOutcome.Held -> {
                gate.hold(outcome.startedAt, System.nanoTime(), outcome.result)
                _state.update { it.copy(completed = it.completed + 1) }
            }

            TargetOutcome.Skipped -> _state.update {
                it.copy(total = it.total - 1, skippedActive = it.skippedActive + 1)
            }
        }
    }

    private suspend fun checkBaseline(
        network: AndroidProbeNetwork,
        gate: BaselineGate<ServerProbeResult>,
        writes: Channel<ServerProbeResult>,
    ): Boolean {
        val startedAt = System.nanoTime()
        val healthy = network.isInternetReachable(BASELINE_TIMEOUT_MS)
        release(gate.onBaseline(healthy, startedAt, System.nanoTime()), writes)
        return healthy
    }

    private fun release(release: BaselineGate.Release<ServerProbeResult>, writes: Channel<ServerProbeResult>) {
        release.confirmed.forEach { count(it, writes) }
        release.unconfirmed.forEach {
            count(it.copy(availability = ServerAvailability.UNKNOWN, detail = ServerProbeDetail.NO_NETWORK), writes)
        }
    }

    private fun count(result: ServerProbeResult, writes: Channel<ServerProbeResult>) {
        _state.update {
            when (result.availability) {
                ServerAvailability.AVAILABLE -> it.copy(available = it.available + 1)
                ServerAvailability.BLOCKED -> it.copy(blocked = it.blocked + 1)
                ServerAvailability.UNKNOWN -> it.copy(unknown = it.unknown + 1)
            }
        }
        // Local failures say nothing about the server, so they never replace an earlier verdict.
        if (result.detail !in LOCAL_FAILURES) writes.trySend(result)
    }

    private suspend fun persist(writes: Channel<ServerProbeResult>) {
        val batch = ArrayList<ServerProbeResult>()
        while (true) {
            val first = writes.receiveCatching().getOrNull() ?: break
            batch += first
            delay(WRITE_BATCH_DELAY_MS)
            while (true) {
                val next = writes.tryReceive().getOrNull() ?: break
                batch += next
            }
            try {
                probeRepository.save(batch)
            } catch (error: Exception) {
                ProtonLogger.e(TAG, "Failed to store scan results", error)
            }
            batch.clear()
        }
    }

    private fun result(
        target: ScanTarget,
        availability: ServerAvailability,
        detail: ServerProbeDetail,
        endpoint: String? = null,
        ports: List<Int> = emptyList(),
        latencyMs: Int? = null,
    ) = ServerProbeResult(
        physicalServerId = target.physical.id,
        logicalServerId = target.logical.id,
        availability = availability,
        detail = detail,
        endpoint = endpoint,
        ports = ports,
        latencyMs = latencyMs,
        checkedAt = System.currentTimeMillis(),
    )

    private suspend fun resolveObfuscation(mode: ServerScanObfuscationMode): AwgObfuscation {
        val enabled = when (mode) {
            // Same rule as the tunnel: AmneziaWG parameters are not used while the proxy chain is on.
            ServerScanObfuscationMode.CONNECTION ->
                !settingsManager.proxyChainEnabled.first() && settingsManager.obfuscationEnabled.first()
            ServerScanObfuscationMode.ALWAYS -> true
            ServerScanObfuscationMode.NEVER -> false
        }
        if (!enabled) return AwgObfuscation.STANDARD
        return AwgObfuscation.fromSettings(
            AwgObfuscationSettings(
                jc = settingsManager.awgJc.first(),
                jmin = settingsManager.awgJmin.first(),
                jmax = settingsManager.awgJmax.first(),
                s1 = settingsManager.awgS1.first(),
                s2 = settingsManager.awgS2.first(),
                s3 = settingsManager.awgS3.first(),
                s4 = settingsManager.awgS4.first(),
                h1 = settingsManager.awgH1.first(),
                h2 = settingsManager.awgH2.first(),
                h3 = settingsManager.awgH3.first(),
                h4 = settingsManager.awgH4.first(),
                i1 = settingsManager.awgI1.first(),
                i2 = settingsManager.awgI2.first(),
                i3 = settingsManager.awgI3.first(),
                i4 = settingsManager.awgI4.first(),
                i5 = settingsManager.awgI5.first(),
                headerProtectionKey = settingsManager.awgHeaderProtectionKey.first(),
                contentPaddingAddition = settingsManager.awgContentPaddingAddition.first(),
            ),
        )
    }

    private suspend fun resolvePorts(preferences: ServerScanPreferences): List<Int> = when (preferences.portMode) {
        ServerScanPortMode.CONNECTION -> settingsManager.vpnPort.first().let { port ->
            // Port 0 means "automatic": the tunnel picks one of the standard ports.
            if (port in ServerScanPreferences.PORT_RANGE) listOf(port) else ServerScanPreferences.STANDARD_PORTS
        }
        ServerScanPortMode.ALL_STANDARD -> ServerScanPreferences.STANDARD_PORTS
        ServerScanPortMode.CUSTOM -> listOf(preferences.customPort)
    }

    private fun dataCheckFor(session: SessionEntity, preferences: ServerScanPreferences): WireGuardProber.DataCheck? {
        if (!preferences.dataCheck) return null
        val tunnelAddress = parseIpv4Literal(session.vpnIpv4?.substringBefore('/')) ?: DEFAULT_TUNNEL_ADDRESS
        val dnsAddress = session.vpnDns?.split(',')?.firstNotNullOfOrNull { parseIpv4Literal(it) } ?: DEFAULT_TUNNEL_DNS
        return WireGuardProber.DataCheck(tunnelAddress, dnsAddress)
    }

    private suspend fun loadServers(session: SessionEntity): List<LogicalServer> {
        val cached = vpnRepository.getCachedServers()
        if (cached.isNotEmpty()) return cached
        return vpnRepository.getServers(session.accessToken, session.sessionId, session.userTier).getOrNull().orEmpty()
    }

    private fun setPhase(phase: ServerScanPhase) {
        _state.update { it.copy(phase = phase) }
    }

    private fun SessionEntity.hasUsableCertificate(): Boolean =
        !wgPrivateKey.isNullOrBlank() && certExpiresAt > System.currentTimeMillis() / 1000 + CERTIFICATE_MARGIN_SECONDS

    private sealed interface TargetOutcome {
        class Final(val result: ServerProbeResult) : TargetOutcome

        class Held(val startedAt: Long, val result: ServerProbeResult) : TargetOutcome

        data object Skipped : TargetOutcome
    }

    private class NetworkLostException : Exception()

    private companion object {
        const val TAG = "ServerScanManager"
        const val CERTIFICATE_MARGIN_SECONDS = 300L
        const val CERTIFICATE_ACTIVATION_DELAY_MS = 3_000L
        const val BASELINE_TIMEOUT_MS = 1_500
        const val BASELINE_INTERVAL_MS = 3_000L
        const val MAX_BASELINE_FAILURES = 3
        const val WRITE_BATCH_DELAY_MS = 500L
        const val TCP_CHECK_PORT = 443

        val BUSY_STATES = setOf(
            AmneziaVpnManager.VpnState.CONNECTING,
            AmneziaVpnManager.VpnState.VERIFYING,
            AmneziaVpnManager.VpnState.DISCONNECTING,
        )
        val LOCAL_FAILURES = setOf(ServerProbeDetail.NO_NETWORK, ServerProbeDetail.NETWORK_ERROR)
        val DEFAULT_TUNNEL_ADDRESS: Inet4Address = ipv4Address(10, 2, 0, 2)
        val DEFAULT_TUNNEL_DNS: Inet4Address = ipv4Address(10, 2, 0, 1)

        fun decodeKey(value: String?): ByteArray? {
            if (value.isNullOrBlank()) return null
            return try {
                Base64.getDecoder().decode(value.trim()).takeIf { it.size == WireGuardCrypto.KEY_SIZE }
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
