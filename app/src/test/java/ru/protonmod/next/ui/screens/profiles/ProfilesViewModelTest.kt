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

package ru.protonmod.next.ui.screens.profiles

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import ru.protonmod.next.data.local.*
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.ui.screens.MainDispatcherRule
import ru.protonmod.next.vpn.AmneziaVpnManager

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var vpnRepository: VpnRepository

    @Mock
    private lateinit var sessionDao: SessionDao

    @Mock
    private lateinit var amneziaVpnManager: AmneziaVpnManager

    @Mock
    private lateinit var profileDao: ProfileDao

    @Mock
    private lateinit var settingsManager: SettingsManager

    private lateinit var connectedServerState: ConnectedServerState
    private lateinit var viewModel: ProfilesViewModel

    private val testServers = listOf(
        LogicalServer(
            id = "us_1", name = "US-FREE-1", tier = 0, features = 0,
            entryCountry = "US", exitCountry = "US", city = "New York",
            averageLoad = 10, servers = listOf(PhysicalServer(id = "p1", domain = "d1", status = 1, load = 10))
        )
    )

    private val testProfiles = listOf(
        VpnProfileEntity(
            id = "p_1", name = "My Profile", protocol = "wireguard",
            port = 0, isObfuscationEnabled = false, obfuscationProfileId = null,
            autoOpenUrl = null, targetServerId = "us_1", targetCountry = "US", targetCity = "New York"
        )
    )

    @Before
    fun setup() = runBlocking {
        MockitoAnnotations.openMocks(this@ProfilesViewModelTest)
        connectedServerState = ConnectedServerState()
        
        whenever(profileDao.getAllProfilesFlow()).thenReturn(flowOf(testProfiles))
        whenever(vpnRepository.getServersFlow()).thenReturn(flowOf(testServers))
        whenever(vpnRepository.getCachedServers()).thenReturn(testServers)
        
        whenever(sessionDao.getSession()).thenReturn(
            SessionEntity(accessToken = "at", refreshToken = "rt", sessionId = "sid", userId = "uid")
        )
        
        whenever(amneziaVpnManager.tunnelState).thenReturn(MutableStateFlow(ru.protonmod.next.vpn.VpnTunnelState.DOWN))
        whenever(amneziaVpnManager.isConnecting).thenReturn(MutableStateFlow(false))
        
        whenever(settingsManager.customProfiles).thenReturn(flowOf(emptyList()))
        whenever(settingsManager.serverLoadDisplayMode).thenReturn(flowOf(ServerLoadDisplayMode.ALL))

        viewModel = ProfilesViewModel(
            context, vpnRepository, sessionDao, amneziaVpnManager,
            connectedServerState, profileDao, settingsManager
        )
    }

    @Test
    fun `profiles state updates from DAO and Repository`() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.profiles.collect() }
        advanceUntilIdle()
        
        val profiles = viewModel.profiles.value
        assertEquals(1, profiles.size)
        assertEquals("My Profile", profiles[0].name)
        assertEquals("US-FREE-1", profiles[0].targetServerName)
        
        collectJob.cancel()
    }

    @Test
    fun `saveProfile calls DAO`() = runTest {
        val newProfile = VpnProfileUiModel(id = "new", name = "New Profile")
        viewModel.saveProfile(newProfile)
        advanceUntilIdle()
        verify(profileDao).insertProfile(any())
    }

    @Test
    fun `deleteProfile calls DAO`() = runTest {
        viewModel.deleteProfile("p_1")
        advanceUntilIdle()
        verify(profileDao).deleteProfileById("p_1")
    }

    @Test
    fun `connectWithProfile calls connect on vpnManager with correct server`() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.profiles.collect() }
        advanceUntilIdle()
        
        val uiProfile = VpnProfileUiModel(id = "p_1", name = "My Profile", targetCountry = "US")
        viewModel.connectWithProfile(uiProfile)
        advanceUntilIdle()
        verify(amneziaVpnManager).connect(eq("us_1"), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull())
        
        collectJob.cancel()
    }
}
