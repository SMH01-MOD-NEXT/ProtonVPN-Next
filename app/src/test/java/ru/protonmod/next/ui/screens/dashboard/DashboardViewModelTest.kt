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

package ru.protonmod.next.ui.screens.dashboard

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import ru.protonmod.next.vpn.VpnTunnelState
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.RecentConnectionDao
import ru.protonmod.next.data.local.RecentConnectionEntity
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.TrafficStatsDao
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.data.model.ObfuscationProfile
import ru.protonmod.next.netshield.LocalNetShield
import ru.protonmod.next.netshield.NetShieldLevel
import ru.protonmod.next.netshield.NetShieldStats
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.ui.screens.MainDispatcherRule
import ru.protonmod.next.vpn.AmneziaVpnManager
import ru.protonmod.next.vpn.VpnAutomationManager

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var connectivityManager: android.net.ConnectivityManager

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var vpnRepository: VpnRepository

    @Mock
    private lateinit var sessionDao: SessionDao

    @Mock
    private lateinit var amneziaVpnManager: AmneziaVpnManager

    @Mock
    private lateinit var vpnAutomationManager: VpnAutomationManager

    @Mock
    private lateinit var connectedServerState: ConnectedServerState

    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var profileDao: ProfileDao

    @Mock
    private lateinit var recentConnectionDao: RecentConnectionDao

    @Mock
    private lateinit var trafficStatsDao: TrafficStatsDao

    @Mock
    private lateinit var localNetShield: LocalNetShield

    @Mock
    private lateinit var powerManager: android.os.PowerManager

    @Mock
    private lateinit var resources: Resources

    private lateinit var viewModel: DashboardViewModel

    private val testServer = LogicalServer(
        id = "us_1", name = "US-FREE-1", tier = 0, features = 0,
        entryCountry = "US", exitCountry = "US", city = "New York",
        servers = listOf(PhysicalServer(id = "p1", domain = "d1", status = 1, load = 10))
    ).apply { averageLoad = 10 }

    private val serversFlow = MutableStateFlow(listOf(testServer))
    private val isUpdatingFlow = MutableStateFlow(false)
    private val vpnStateFlow = MutableStateFlow(AmneziaVpnManager.VpnState.DISCONNECTED)
    private val certStateFlow = MutableStateFlow(AmneziaVpnManager.CertificateState.Valid)
    private val connectedServerFlow = MutableStateFlow<LogicalServer?>(null)
    private val speedFlow = MutableStateFlow<String?>(null)
    private val trafficRxFlow = MutableStateFlow<String?>(null)
    private val trafficTxFlow = MutableStateFlow<String?>(null)
    private val tunnelStateFlow = MutableStateFlow(VpnTunnelState.DOWN)
    
    // Additional flows from SettingsManager
    private val quickConnectStrategyFlow = MutableStateFlow("fastest")
    private val quickConnectTargetIdFlow = MutableStateFlow<String?>(null)
    private val serverLoadDisplayModeFlow = MutableStateFlow(ServerLoadDisplayMode.ALL)
    private val autoConnectEnabledFlow = MutableStateFlow(false)
    private val apiBypassEnabledFlow = MutableStateFlow(false)
    private val apiBypassStrategyFlow = MutableStateFlow("none")
    private val customProfilesFlow = MutableStateFlow<List<ObfuscationProfile>>(emptyList())
    private val isIpHiddenFlow = MutableStateFlow(false)
    private val pauseEndTimeFlow = MutableStateFlow(0L)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        whenever(context.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.getBoolean(any(), any())).thenReturn(false)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        whenever(context.packageName).thenReturn("ru.protonmod.next")
        whenever(powerManager.isIgnoringBatteryOptimizations(any())).thenReturn(true)
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getString(any())).thenReturn("Error")
        whenever(context.getString(any())).thenReturn("Unknown")
        
        whenever(vpnRepository.getServersFlow()).thenReturn(serversFlow)
        runBlocking {
            whenever(vpnRepository.getCachedServers()).thenReturn(listOf(testServer) )
            whenever(vpnRepository.getServers(any(), any(), any(), any())).thenReturn(Result.success(listOf(testServer)))
        }
        whenever(vpnRepository.isUpdating).thenReturn(isUpdatingFlow)
        
        whenever(amneziaVpnManager.vpnState).thenReturn(vpnStateFlow)
        whenever(amneziaVpnManager.isConnecting).thenReturn(MutableStateFlow(false))
        whenever(amneziaVpnManager.certState).thenReturn(certStateFlow)
        whenever(amneziaVpnManager.speed).thenReturn(speedFlow)
        whenever(amneziaVpnManager.trafficRx).thenReturn(trafficRxFlow)
        whenever(amneziaVpnManager.trafficTx).thenReturn(trafficTxFlow)
        whenever(amneziaVpnManager.connectionWarning).thenReturn(MutableStateFlow(null))
        whenever(amneziaVpnManager.tunnelState).thenReturn(tunnelStateFlow)
        
        whenever(connectedServerState.connectedServer).thenReturn(connectedServerFlow)
        whenever(recentConnectionDao.getRecentConnections()).thenReturn(MutableStateFlow(emptyList()))
        whenever(profileDao.getAllProfilesFlow()).thenReturn(MutableStateFlow(emptyList()))
        whenever(trafficStatsDao.observeAll()).thenReturn(flowOf(emptyList()))
        
        whenever(settingsManager.quickConnectStrategy).thenReturn(quickConnectStrategyFlow)
        whenever(settingsManager.quickConnectTargetId).thenReturn(quickConnectTargetIdFlow)
        whenever(settingsManager.serverLoadDisplayMode).thenReturn(serverLoadDisplayModeFlow)
        whenever(settingsManager.autoConnectEnabled).thenReturn(autoConnectEnabledFlow)
        whenever(settingsManager.apiBypassEnabled).thenReturn(apiBypassEnabledFlow)
        whenever(settingsManager.apiBypassStrategy).thenReturn(apiBypassStrategyFlow)
        whenever(settingsManager.customProfiles).thenReturn(customProfilesFlow)
        whenever(settingsManager.isIpHidden).thenReturn(isIpHiddenFlow)
        whenever(settingsManager.pauseEndTime).thenReturn(pauseEndTimeFlow)
        whenever(settingsManager.trafficStatsEnabled).thenReturn(flowOf(true))
        whenever(settingsManager.netShieldLevel).thenReturn(flowOf(NetShieldLevel.DISABLED))
        whenever(localNetShield.stats).thenReturn(MutableStateFlow(NetShieldStats()))
        
        
        val testSession = SessionEntity(
            accessToken = "token", 
            refreshToken = "refresh", 
            sessionId = "session_id", 
            userId = "user_id", 
            userTier = 0
        )
        runBlocking {
            whenever(sessionDao.getSession()).thenReturn(testSession)
        }

        viewModel = DashboardViewModel(
            context,
            vpnRepository,
            sessionDao,
            settingsManager,
            amneziaVpnManager,
            vpnAutomationManager,
            connectedServerState,
            profileDao,
            recentConnectionDao,
            trafficStatsDao,
            localNetShield
        )
    }

    @Test
    fun `initial state becomes Success after loading servers`() = runTest {
        // uiState uses WhileSubscribed(5000), so it only starts when collected.
        // first() will start collection and wait for the first value.
        // Since we want to ensure it's Success, we can filter or just take the first one after advanceUntilIdle
        
        val state = viewModel.uiState.first { it is DashboardUiState.Success }
        
        System.err.println("Dashboard current state: $state")
        assertTrue("Expected Success but was $state", state is DashboardUiState.Success)
    }

    @Test
    fun `dashboard updates when VPN state changes`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        // Ensure initial state is Success
        viewModel.uiState.first { it is DashboardUiState.Success }
        
        vpnStateFlow.value = AmneziaVpnManager.VpnState.CONNECTED
        
        val state = viewModel.uiState.first { it is DashboardUiState.Success && it.isConnected }
        assertTrue("Expected isConnected=true", (state as DashboardUiState.Success).isConnected)
    }

    @Test
    fun `disconnect is available while VPN is connecting`() = runTest {
        vpnStateFlow.value = AmneziaVpnManager.VpnState.CONNECTING

        viewModel.disconnect()
        advanceUntilIdle()

        verify(connectedServerState).setConnectedServer(null)
        verify(amneziaVpnManager).disconnect()
    }
}
