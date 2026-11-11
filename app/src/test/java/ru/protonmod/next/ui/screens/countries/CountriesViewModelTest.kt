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

package ru.protonmod.next.ui.screens.countries

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.network.probe.AvailabilityBadge
import ru.protonmod.next.data.network.probe.ServerAvailability
import ru.protonmod.next.data.network.probe.ServerProbeDetail
import ru.protonmod.next.data.network.probe.ServerProbeResult
import ru.protonmod.next.data.repository.ServerProbeRepository
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.ui.screens.MainDispatcherRule
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import ru.protonmod.next.vpn.AmneziaVpnManager

@OptIn(ExperimentalCoroutinesApi::class)
class CountriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var context: android.content.Context

    @Mock
    private lateinit var vpnRepository: VpnRepository

    @Mock
    private lateinit var sessionDao: SessionDao

    @Mock
    private lateinit var amneziaVpnManager: AmneziaVpnManager

    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var serverProbeRepository: ServerProbeRepository

    private lateinit var connectedServerState: ConnectedServerState
    private lateinit var viewModel: CountriesViewModel
    private val probeResults = MutableStateFlow<Map<String, ServerProbeResult>>(emptyMap())
    private val testDispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = mainDispatcherRule.testDispatcher
        override fun io(): CoroutineDispatcher = mainDispatcherRule.testDispatcher
        override fun default(): CoroutineDispatcher = mainDispatcherRule.testDispatcher
    }

    private val testServers = listOf(
        LogicalServer(
            id = "us_1", name = "US-FREE-1", tier = 0, features = 0,
            entryCountry = "US", exitCountry = "US", city = "New York",
            averageLoad = 10, servers = listOf(PhysicalServer(id = "p1", domain = "d1", status = 1, load = 10))
        ),
        LogicalServer(
            id = "us_2", name = "US-FREE-2", tier = 0, features = 0,
            entryCountry = "US", exitCountry = "US", city = "Los Angeles",
            averageLoad = 20, servers = listOf(PhysicalServer(id = "p2", domain = "d2", status = 1, load = 20))
        ),
        LogicalServer(
            id = "de_1", name = "DE-FREE-1", tier = 0, features = 0,
            entryCountry = "DE", exitCountry = "DE", city = "Frankfurt",
            averageLoad = 30, servers = listOf(PhysicalServer(id = "p3", domain = "d3", status = 1, load = 30))
        )
    )

    @Before
    fun setup() = runBlocking {
        MockitoAnnotations.openMocks(this@CountriesViewModelTest)
        connectedServerState = ConnectedServerState()
        
        whenever(vpnRepository.getServersFlow()).thenReturn(flowOf(testServers))
        whenever(vpnRepository.isUpdating).thenReturn(MutableStateFlow(false))
        whenever(sessionDao.getSession()).thenReturn(
            SessionEntity(accessToken = "at", refreshToken = "rt", sessionId = "sid", userId = "uid")
        )
        whenever(vpnRepository.getServers(any(), any(), any(), any())).thenReturn(Result.success(testServers))
        whenever(vpnRepository.getCachedServers()).thenReturn(testServers)
        
        whenever(amneziaVpnManager.tunnelState).thenReturn(MutableStateFlow(ru.protonmod.next.vpn.VpnTunnelState.DOWN))
        whenever(amneziaVpnManager.isConnecting).thenReturn(MutableStateFlow(false))
        whenever(settingsManager.serverLoadDisplayMode).thenReturn(flowOf(ServerLoadDisplayMode.ALL))
        whenever(settingsManager.torModeEnabled).thenReturn(flowOf(false))
        whenever(settingsManager.warpPreferences).thenReturn(flowOf(ru.protonmod.next.data.local.WarpPreferences()))

        whenever(sessionDao.getSessionFlow()).thenReturn(flowOf(null))
        whenever(serverProbeRepository.results).thenReturn(probeResults)
        viewModel = CountriesViewModel(context, vpnRepository, sessionDao, amneziaVpnManager, connectedServerState, settingsManager, kotlinx.coroutines.CoroutineScope(Dispatchers.Main), serverProbeRepository, testDispatcherProvider, mock<ru.protonmod.next.data.repository.CustomConfigRepository> { on { configs } doReturn MutableStateFlow(emptyList()) }, mock<ru.protonmod.next.data.repository.WarpRepository>())
    }

    @Test
    fun `initial state shows country list`() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue("Expected Success but was $state", state is CountriesUiState.Success)
        val countries = (state as CountriesUiState.Success).countries
        assertEquals(2, countries.size)
        assertEquals("DE", countries[0].code)
        assertEquals("US", countries[1].code)
        
        collectJob.cancel()
    }

    @Test
    fun `expandCitiesForCountry updates bottomSheetContent to Cities`() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        viewModel.expandCitiesForCountry("US")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state is CountriesUiState.Success)
        val content = (state as CountriesUiState.Success).bottomSheetContent
        assertTrue("Expected BottomSheetContent.Cities but was $content", content is BottomSheetContent.Cities)
        assertEquals(2, (content as BottomSheetContent.Cities).cities.size)
        
        collectJob.cancel()
    }

    @Test
    fun `expandServersForCity updates bottomSheetContent to Servers`() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        viewModel.expandCitiesForCountry("US")
        viewModel.expandServersForCity("New York")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state is CountriesUiState.Success)
        val content = (state as CountriesUiState.Success).bottomSheetContent
        assertTrue("Expected BottomSheetContent.Servers but was $content", content is BottomSheetContent.Servers)
        assertEquals(1, (content as BottomSheetContent.Servers).servers.size)
        
        collectJob.cancel()
    }

    @Test
    fun `selectCountry calls connect on vpnManager`() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        viewModel.selectCountry("US")
        advanceUntilIdle()
        verify(amneziaVpnManager).connect(eq("us_1"), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())
        
        collectJob.cancel()
    }

    @Test
    fun `selectServer calls connect on vpnManager`() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        viewModel.selectServer(testServers[2]) // DE-FREE-1
        advanceUntilIdle()
        verify(amneziaVpnManager).connect(eq("de_1"), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())
        
        collectJob.cancel()
    }

    @Test fun `country connection survives leaving the screen`() = runTest {
        val store = androidx.lifecycle.ViewModelStore()
        store.put("countries", viewModel)
        viewModel.selectCountry("US")
        store.clear()
        advanceUntilIdle()
        verify(amneziaVpnManager).connect(eq("us_1"), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun scannerResultsProduceAvailabilityBadges() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        probeResults.value = mapOf(
            "p1" to probeResult("p1", "us_1", ServerAvailability.AVAILABLE),
            "p2" to probeResult("p2", "us_2", ServerAvailability.BLOCKED),
            "p3" to probeResult("p3", "de_1", ServerAvailability.AVAILABLE),
        )

        val state = viewModel.uiState.first {
            (it as? CountriesUiState.Success)?.availability?.servers?.size == 3
        } as CountriesUiState.Success

        assertEquals(AvailabilityBadge.ALL_AVAILABLE, state.availability.server("us_1"))
        assertEquals(AvailabilityBadge.ALL_BLOCKED, state.availability.server("us_2"))
        assertEquals(AvailabilityBadge.PARTIAL, state.availability.country("US"))
        assertEquals(AvailabilityBadge.ALL_AVAILABLE, state.availability.country("DE"))
        assertEquals(AvailabilityBadge.ALL_AVAILABLE, state.availability.city("US", "New York"))
        assertEquals(AvailabilityBadge.ALL_BLOCKED, state.availability.city("US", "Los Angeles"))
        collectJob.cancel()
    }

    private fun probeResult(physicalId: String, logicalId: String, availability: ServerAvailability) = ServerProbeResult(
        physicalServerId = physicalId,
        logicalServerId = logicalId,
        availability = availability,
        detail = if (availability == ServerAvailability.AVAILABLE) ServerProbeDetail.HANDSHAKE_VERIFIED else ServerProbeDetail.PROTOCOL_BLOCKED,
        endpoint = null,
        ports = listOf(443),
        latencyMs = null,
        checkedAt = 0L,
    )
}
