/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.ui.screens.settings

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.ip.IpEchoSources
import ru.protonmod.next.data.model.eventbypass.EventBypassCache
import ru.protonmod.next.data.model.eventbypass.EventBypassEntry
import ru.protonmod.next.ui.screens.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class IpLookupSettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @Test fun `event toggle reports preference failure instead of an uncaught UI exception`() = runTest {
        val catalogue = IpEchoSources.catalogue(EventBypassCache.encode(listOf(
            EventBypassEntry(id = "vercel", name = "Vercel", url = "https://dynamic.vercel.app/api/", enabled = true),
        )))
        val settings = mock<SettingsManager>()
        whenever(settings.ipLookupServices).thenReturn(flowOf(IpEchoSources.DEFAULT_ENABLED_IDS))
        whenever(settings.ipLookupCatalogue).thenReturn(flowOf(catalogue))
        whenever(settings.setIpLookupService(any(), any())).thenAnswer { throw IOException("disk error") }
        val vm = IpLookupSettingsViewModel(settings)
        vm.setEnabled(setOf(catalogue.first { it.kind == IpEchoSources.Kind.EVENT }.id), true)
        advanceUntilIdle()
        assertTrue(vm.updateFailed.value)
        reset(settings)
        vm.disableAll()
        advanceUntilIdle()
        assertFalse(vm.updateFailed.value)
        verify(settings).disableIpLookup()
    }
}
