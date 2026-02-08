package com.protonvpn.android.vpn.wireguard

/*
 * Copyright (c) 2021 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

import android.content.Context
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.ConnError
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsWireguard
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.models.vpn.wireguard.WireGuardTunnel
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.Server
import com.protonvpn.android.statistics.domain.VpnConnectionTracker
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.NetworkCapabilitiesFlow
import com.protonvpn.android.vpn.PrepareForConnection
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.usecases.ServerNameTopStrategyEnabled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import okhttp3.OkHttpClient
import org.amnezia.awg.backend.BackendException
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

enum class ServerNameStrategy(val value: Int) {
    ServerNameRandom(0),
    ServerNameTop(1),
}

@Singleton
class WireguardBackend @Inject constructor(
    @ApplicationContext val context: Context,
    networkManager: NetworkManager,
    networkCapabilitiesFlow: NetworkCapabilitiesFlow,
    settingsForConnection: SettingsForConnection,
    certificateRepository: CertificateRepository,
    dispatcherProvider: VpnDispatcherProvider,
    mainScope: CoroutineScope,
    localAgentUnreachableTracker: LocalAgentUnreachableTracker,
    currentUser: CurrentUser,
    getNetZone: GetNetZone,
    private val prepareForConnection: PrepareForConnection,
    private val computeAllowedIPs: ComputeAllowedIPs,
    foregroundActivityTracker: ForegroundActivityTracker,
    vpnConnectionTracker: VpnConnectionTracker,
    @SharedOkHttpClient okHttp: OkHttpClient,
    private val serverNameTopStrategyEnabled: ServerNameTopStrategyEnabled,
) : VpnBackend(
    settingsForConnection, certificateRepository, networkManager, networkCapabilitiesFlow, VpnProtocol.WireGuard, mainScope,
    dispatcherProvider, localAgentUnreachableTracker, currentUser, getNetZone, foregroundActivityTracker, vpnConnectionTracker, okHttp
) {
    private val wireGuardIo = dispatcherProvider.newSingleThreadDispatcherForInifiniteIo()

    // AmneziaWG: Use the updated GoBackend with your ContextWrapper
    private val backend: GoBackend by lazy { GoBackend(WireguardContextWrapper(context)) }

    private var monitoringJob: Job? = null
    private var service: WireguardWrapperService? = null

    // AmneziaWG: Using the adapted WireGuardTunnel class
    private val testTunnel = WireGuardTunnel(
        name = Constants.WIREGUARD_TUNNEL_NAME,
        config = null,
        state = Tunnel.State.DOWN
    )

    override suspend fun prepareForConnection(
        connectIntent: AnyConnectIntent,
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<PrepareResult> {
        val protocolInfo = prepareForConnection.prepare(server, vpnProtocol, transmissionProtocols, scan,
            numberOfPorts, waitForAll, PRIMARY_PORT, includeTls = true)
        return protocolInfo.map {
            PrepareResult(
                this,
                ConnectionParamsWireguard(
                    connectIntent,
                    server,
                    it.port,
                    it.connectingDomain,
                    it.entryIp,
                    it.transmissionProtocol,
                    settingsForConnection.getFor(connectIntent).ipV6Enabled
                )
            )
        }
    }

    override suspend fun connect(connectionParams: ConnectionParams) {

        super.connect(connectionParams)

        // We need to start ignoring state changes for the old connection
        monitoringJob?.cancel()

        vpnProtocolState = VpnState.Connecting
        val wireguardParams = connectionParams as ConnectionParamsWireguard
        try {
            val settings = settingsForConnection.getFor(wireguardParams.connectIntent)

            // AmneziaWG: This will generate the Amnezia config (with Jc, Jmin etc if implemented in Params)
            val config = wireguardParams.getTunnelConfig(
                context, settings, currentUser.sessionId(), certificateRepository, computeAllowedIPs
            )

            // Proton logic for Strategy - kept for logic, but removed from setState as Amnezia GoBackend
            // usually relies on Config object, not setState params for obfuscation/strategies.
            val serverNameStrategy =
                if (serverNameTopStrategyEnabled()) ServerNameStrategy.ServerNameTop
                else ServerNameStrategy.ServerNameRandom

            withContext(wireGuardIo) {
                try {
                    // AmneziaWG adaptation:
                    // Standard Amnezia/WireGuard GoBackend setState signature is (Tunnel, State, Config).
                    // Removed 'transmissionStr' and 'serverNameStrategy' as they are specific to Proton's modified backend.
                    // If you need TCP/Obfuscation, it should be part of the 'config' object generated above.
                    backend.setState(testTunnel, Tunnel.State.UP, config)
                } catch (e: BackendException) {
                    // Handling timeout specifically
                    if (e.reason == BackendException.Reason.UNABLE_TO_START_VPN && e.cause is TimeoutException) {
                        // Retry once
                        backend.setState(testTunnel, Tunnel.State.UP, config)
                    } else {
                        throw e
                    }
                }
                startMonitoringJob()
            }
        } catch (e: SecurityException) {
            if (e.message?.contains("INTERACT_ACROSS_USERS") == true)
                selfStateFlow.value = VpnState.Error(ErrorType.MULTI_USER_PERMISSION, isFinal = true)
            else
                handleConnectException(e)
        } catch (e: IllegalStateException) {
            if (e is CancellationException) throw e
            else handleConnectException(e)
        } catch (e: BackendException) {
            handleConnectException(e)
        }
    }

    private fun startMonitoringJob() {
        monitoringJob = mainScope.launch(dispatcherProvider.infiniteIo) {
            ProtonLogger.logCustom(LogCategory.CONN_WIREGUARD, "start monitoring job")

            val networkJob = launch(dispatcherProvider.Main) {
                networkManager.observe().collect { status ->
                    val isConnected = status != NetworkStatus.Disconnected
                    ProtonLogger.logCustom(LogCategory.CONN_WIREGUARD, "network status changed: $isConnected")

                    // AmneziaWG/Standard WireGuard adaptation:
                    // Standard GoBackend does not expose setNetworkAvailable publicly.
                    // It handles network changes internally.
                    // Uncomment only if you have patched org.amnezia.awg.backend.GoBackend
                    /*
                    withContext(wireGuardIo) {
                        backend.setNetworkAvailable(isConnected)
                    }
                    */
                }
            }

            try {
                // AmneziaWG adaptation:
                // Replaced the 'while' loop and integer state polling (Proton specific)
                // with the Flow observer from WireGuardTunnel (Standard Android/WG pattern).
                // This reacts to state changes propagated by GoBackend to the Tunnel instance.
                testTunnel.stateFlow.collect { state ->
                    ensureActive()

                    val newState: VpnState = when (state) {
                        Tunnel.State.UP -> VpnState.Connected
                        Tunnel.State.DOWN -> VpnState.Disabled // Or Disconnected
                        Tunnel.State.TOGGLE -> VpnState.Connecting
                        else -> VpnState.Error(ErrorType.GENERIC_ERROR, isFinal = false)
                    }

                    withContext(dispatcherProvider.Main) {
                        if (vpnProtocolState != newState) {
                            // Only update if changed to avoid loops
                            vpnProtocolState = newState
                        }
                    }
                }
            } finally {
                networkJob.cancel()
                ProtonLogger.logCustom(LogCategory.CONN_WIREGUARD, "stop monitoring job")
            }
        }
    }

    override suspend fun closeVpnTunnel(withStateChange: Boolean) {
        service?.close()
        if (withStateChange) {
            // Set state to disabled right away to give app some time to close notification
            // as the service might be killed right away on disconnection
            vpnProtocolState = VpnState.Disabled
            delay(10)
        }
        withContext(wireGuardIo) {
            // AmneziaWG adaptation: standard setState signature
            backend.setState(testTunnel, Tunnel.State.DOWN, null)
        }
    }

    fun serviceCreated(vpnService: WireguardWrapperService) {
        service = vpnService
    }

    fun serviceDestroyed() {
        service = null
    }

    private fun handleConnectException(e: Exception) {
        ProtonLogger.log(
            ConnError,
            "Caught exception while connecting with WireGuard\n" +
                    StringWriter().apply { e.printStackTrace(PrintWriter(this)) }.toString()
        )
        // TODO do not use generic error here (depends on other branch)
        selfStateFlow.value = VpnState.Error(ErrorType.GENERIC_ERROR, isFinal = true)
    }

    companion object {
        private const val PRIMARY_PORT = 443
    }
}