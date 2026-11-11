/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network.warp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import io.nekohasekai.libbox.Libbox
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.repository.CustomConfigRepository
import ru.protonmod.next.data.repository.WarpRepository
import ru.protonmod.next.data.network.Socks5Socket
import ru.protonmod.next.vpn.AwgBoxPlatform
import ru.protonmod.next.vpn.ImportedWireGuard
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class WarpApiManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsManager,
    private val configs: CustomConfigRepository,
    private val generation: Provider<WarpRepository>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean()
    private val session = WarpApiSession()
    internal val state get() = session.state
    val status = state.map { when (it) {
        WarpApiState.Stopped -> WarpApiStatus.STOPPED
        WarpApiState.Starting -> WarpApiStatus.STARTING
        WarpApiState.Retrying -> WarpApiStatus.RETRYING
        WarpApiState.Failed -> WarpApiStatus.FAILED
        is WarpApiState.Ready -> WarpApiStatus.READY
    } }.stateIn(scope, SharingStarted.Eagerly, WarpApiStatus.STOPPED)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try { configs.load() } catch (_: Exception) { session.failed(); return@launch }
            combine(settings.apiBypassEnabled, settings.apiBypassStrategy, settings.warpPreferences,
                configs.configs, warpApiNetworks(context)) { values: Array<Any?> ->
                values.toList()
            }.distinctUntilChanged().catch { session.failed() }.collectLatest { values ->
                session.stopped()
                val network = values[4] as WarpApiNetwork
                val preferences = values[2] as ru.protonmod.next.data.local.WarpPreferences
                if (values[0] != true || values[1] != SettingsManager.STRATEGY_WARP || network.vpn) return@collectLatest
                if (!preferences.enabled) { session.failed(); return@collectLatest }
                if (network.handle == null) return@collectLatest
                try {
                    preferences.validate()
                    val saved = configs.configs.value.firstOrNull { it.isWarp }
                    val settingsKey = preferences.copy(enabled = false).toString()
                    if (saved == null || (saved.warpSettingsKey.isNotEmpty() && saved.warpSettingsKey != settingsKey)) {
                        generation.get().generateAndSave(preferences)
                        return@collectLatest // encrypted repository emission starts the next generation
                    }
                    if (saved.warpSettingsKey.isEmpty()) {
                        // Adopt legacy saved keys once instead of forcing registration
                        // on upgrade. Subsequent parameter edits invalidate this marker.
                        configs.saveWarp(saved.link, settingsKey)
                        return@collectLatest
                    }
                    // Reuse the encrypted account/config; retry never re-registers.
                    val raw = ImportedWireGuard.decode(saved.link)
                    val peerHost = Regex("(?m)^Endpoint = (.+):[0-9]+$").find(raw)?.groupValues?.get(1)
                        ?: throw IOException("Invalid WARP endpoint")
                    val connectivity = context.getSystemService(ConnectivityManager::class.java)
                    val physical = (listOfNotNull(connectivity.activeNetwork) + connectivity.allNetworks).distinct().firstOrNull { n -> connectivity.getNetworkCapabilities(n)?.let {
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    } == true } ?: throw IOException("No physical network")
                    val numeric = if (peerHost == "engage.cloudflareclient.com") "162.159.193.1" else if (peerHost.matches(Regex("[0-9]+(\\.[0-9]+){3}"))) peerHost else
                        withContext(Dispatchers.IO) { physical.getAllByName(peerHost).filterIsInstance<Inet4Address>().first().hostAddress!! }
                    val link = ImportedWireGuard.encode(raw.replace(Regex("(?m)^Endpoint = .+:([0-9]+)$"), "Endpoint = $numeric:$1"))
                    val username = secret(); val password = secret()
                    try {
                        session.run {
                            val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
                            val proxy = WarpApiProxy(port, username, password)
                            val native = Libbox.newApiWarpProxy(WarpApiConfig.build(link, port, username, password), AwgBoxPlatform(context))
                            // Trigger encrypted traffic with a cancellable loopback probe. UAPI,
                            // not TCP success, determines readiness; the remote TCP peer may filter.
                            val probe = Socks5Socket("127.0.0.1", port, username, password)
                            val engine = object : WarpApiEngine {
                                var job: Job? = null
                                override fun handshakeConfirmed() = native.handshakeConfirmed()
                                override fun stimulateHandshake() {
                                    job = scope.launch { try {
                                        probe.soTimeout = 5_000
                                        probe.connect(InetSocketAddress.createUnresolved("1.1.1.1", 443), 1_000)
                                    } catch (_: Exception) { } finally { probe.close() } }
                                }
                                override fun close() { probe.close(); job?.cancel(); native.close() }
                            }
                            engine to proxy
                        }
                    } finally { session.stopped() }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { session.failed() }
            }
        }
    }

    /** Called only on OkHttp's worker thread. Caller cancellation and deadline
     * terminate waiting; no request is allowed to escape through a direct socket. */
    internal fun awaitReady(cancelled: () -> Boolean): WarpApiProxy = runBlocking {
        start()
        withTimeoutOrNull(30_000) {
            while (true) {
                if (cancelled()) throw IOException("Canceled")
                if (!settings.isApiBypassEnabledSync() || settings.getApiBypassStrategySync() != SettingsManager.STRATEGY_WARP)
                    throw IOException("WARP bypass changed")
                when (val current = state.value) {
                    is WarpApiState.Ready -> return@withTimeoutOrNull current.proxy
                    WarpApiState.Failed -> throw IOException("WARP configuration unavailable")
                    else -> delay(50)
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        } ?: throw IOException("WARP handshake is not ready")
    }
    private fun secret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) })
}

enum class WarpApiStatus { STOPPED, STARTING, RETRYING, READY, FAILED }
