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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.*
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.VpnProfileEntity
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.VpnRepository

@OptIn(ExperimentalCoroutinesApi::class)
class VpnQuickConnectTest {
    private val manager = mock<AmneziaVpnManager>()
    private val repository = mock<VpnRepository>()
    private val sessions = mock<SessionDao>()
    private val settings = mock<SettingsManager>()
    private val profiles = mock<ProfileDao>()
    private val connector = VpnQuickConnect(manager, repository, sessions, settings, profiles)
    private val session = SessionEntity(accessToken = "test", refreshToken = "test",
        sessionId = "session", userId = "user")
    private val physical = PhysicalServer(id = "physical", domain = "vpn.example.test", status = 1)
    private val fastest = LogicalServer(id = "fast", name = "Fast", tier = 0, features = 0,
        entryCountry = "NL", exitCountry = "NL", city = "Amsterdam", servers = listOf(physical),
        averageLoad = 10)
    private val other = fastest.copy(id = "other", averageLoad = 80)

    private suspend fun configure(strategy: String, target: String? = null) {
        whenever(sessions.getSession()).thenReturn(session)
        whenever(settings.quickConnectStrategy).thenReturn(flowOf(strategy))
        whenever(settings.quickConnectTargetId).thenReturn(flowOf(target))
        whenever(repository.getCachedServers()).thenReturn(listOf(other, fastest))
    }

    @Test
    fun `no session does not start a connection`() = runTest {
        connector.connect()
        verifyNoInteractions(manager, repository, settings, profiles)
    }

    @Test
    fun `empty cache does not start a connection`() = runTest {
        configure("fastest")
        whenever(repository.getCachedServers()).thenReturn(emptyList())
        connector.connect()
        verifyNoInteractions(manager)
    }

    @Test
    fun `fastest and recent keep their existing selection behavior`() = runTest {
        for (strategy in listOf("fastest", "recent", "unknown")) {
            configure(strategy)
            connector.connect()
        }
        verify(manager, times(3)).connect(fastest.id, physical, session, logicalServer = fastest)
    }

    @Test
    fun `specific server is selected even when not fastest`() = runTest {
        configure("server", other.id)
        connector.connect()
        verify(manager).connect(other.id, physical, session, logicalServer = other)
    }

    @Test
    fun `missing server or profile falls back to fastest`() = runTest {
        configure("server", "removed")
        connector.connect()
        configure("profile", "removed")
        whenever(profiles.getAllProfilesFlow()).thenReturn(flowOf(emptyList()))
        connector.connect()
        verify(manager, times(2)).connect(fastest.id, physical, session, logicalServer = fastest)
    }

    @Test
    fun `profile preserves port and obfuscation options`() = runTest {
        configure("profile", "profile")
        val profile = VpnProfileEntity(id = "profile", name = "Test", protocol = "wireguard",
            port = 8443, isObfuscationEnabled = true, obfuscationProfileId = "standard_1",
            autoOpenUrl = null, targetServerId = other.id, targetCountry = null, targetCity = null)
        whenever(profiles.getAllProfilesFlow()).thenReturn(flowOf(listOf(profile)))
        whenever(repository.findBestServerForProfile(profile, listOf(other, fastest))).thenReturn(other)
        whenever(settings.customProfiles).thenReturn(flowOf(emptyList()))
        connector.connect()
        verify(manager).connect(eq(other.id), eq(physical), eq(session), eq(8443), eq(true),
            argThat { jc > 0 }, eq(other), eq(false))
    }

    @Test
    fun `standalone quick connect resumes without a service`() = runTest {
        configure("fastest")
        val gate = CompletableDeferred<Unit>()
        whenever(settings.quickConnectStrategy).thenReturn(kotlinx.coroutines.flow.flow {
            gate.await()
            emit("fastest")
        })
        val job = launch { connector.connect() }
        runCurrent()
        gate.complete(Unit)
        job.join()
        verify(manager).connect(fastest.id, physical, session, logicalServer = fastest)
    }

}
