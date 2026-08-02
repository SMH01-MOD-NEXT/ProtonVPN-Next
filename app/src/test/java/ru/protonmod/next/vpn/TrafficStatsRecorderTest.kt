/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.vpn

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.TrafficStatsDao

class TrafficStatsRecorderTest {

    @Test
    fun `database failure keeps pending deltas and never escapes`() = runTest {
        val dao = mock<TrafficStatsDao>()
        val settings = mock<SettingsManager>()
        whenever(settings.trafficStatsEnabled).thenReturn(flowOf(true))
        whenever(dao.addDelta(any(), any(), any(), any()))
            .thenThrow(IllegalStateException("database is locked"))
            .thenReturn(Unit)
        val recorder = TrafficStatsRecorder(dao, settings)

        recorder.record(deltaRx = 10, deltaTx = 20, deltaSeconds = 1)
        recorder.flush()

        verify(dao, times(2)).addDelta(any(), org.mockito.kotlin.eq(10), org.mockito.kotlin.eq(20), org.mockito.kotlin.eq(1))
    }
}
