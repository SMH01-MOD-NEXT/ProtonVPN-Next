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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.TrafficStatsDao
import ru.protonmod.next.utils.ProtonLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists per-day VPN traffic statistics (rx/tx bytes + usage seconds).
 *
 * The VPN service reports deltas every second; writing to Room at that rate
 * would be wasteful, so deltas are buffered in memory and flushed:
 *  - every [FLUSH_INTERVAL_MS],
 *  - when the calendar day changes (so midnight boundaries stay accurate),
 *  - explicitly via [flush] when the session ends.
 */
@Singleton
class TrafficStatsRecorder @Inject constructor(
    private val trafficStatsDao: TrafficStatsDao,
    private val settingsManager: SettingsManager,
) {
    private val mutex = Mutex()

    private var pendingDay: String? = null
    private var pendingRx = 0L
    private var pendingTx = 0L
    private var pendingSeconds = 0L
    private var lastFlushAtMs = 0L

    suspend fun record(deltaRx: Long, deltaTx: Long, deltaSeconds: Long) {
        // Respect the user's privacy toggle: when stats are disabled, drop data.
        if (!settingsManager.trafficStatsEnabled.first()) return

        mutex.withLock {
            val today = currentDay()
            if (pendingDay != null && pendingDay != today) {
                // Attribute everything accumulated so far to the previous day.
                flushLocked()
            }
            pendingDay = today
            pendingRx += deltaRx.coerceAtLeast(0L)
            pendingTx += deltaTx.coerceAtLeast(0L)
            pendingSeconds += deltaSeconds.coerceAtLeast(0L)

            val now = System.currentTimeMillis()
            if (now - lastFlushAtMs >= FLUSH_INTERVAL_MS) {
                flushLocked()
            }
        }
    }

    /** Flushes any buffered deltas. Safe to call at any time (e.g. on disconnect). */
    suspend fun flush() {
        mutex.withLock { flushLocked() }
    }

    private suspend fun flushLocked() {
        val day = pendingDay
        if (day == null || (pendingRx <= 0L && pendingTx <= 0L && pendingSeconds <= 0L)) {
            pendingDay = null
            lastFlushAtMs = System.currentTimeMillis()
            return
        }

        runCatching {
            trafficStatsDao.addDelta(day, pendingRx, pendingTx, pendingSeconds)
        }.onSuccess {
            pendingRx = 0L
            pendingTx = 0L
            pendingSeconds = 0L
            pendingDay = null
        }.onFailure { error ->
            // Statistics are optional. Retain deltas for the next flush, but never let a transient
            // SQLITE_BUSY or storage error terminate the VPN/UI process.
            ProtonLogger.w("TrafficStatsRecorder", "Deferred traffic stats flush: ${error.message}")
        }
        lastFlushAtMs = System.currentTimeMillis()
    }

    private fun currentDay(): String = DAY_FORMAT.get()!!.format(Date())

    private companion object {
        const val FLUSH_INTERVAL_MS = 15_000L

        /** SimpleDateFormat is not thread-safe; keep one instance per thread. */
        val DAY_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
    }
}
