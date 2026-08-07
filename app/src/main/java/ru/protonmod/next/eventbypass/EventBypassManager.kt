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

package ru.protonmod.next.eventbypass

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.data.repository.EventBypassRepository
import ru.protonmod.next.data.repository.EventBypassResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing state of the event bypass synchronisation. */
data class EventBypassSyncState(
    val isRefreshing: Boolean = false,
    val lastResult: EventBypassResult? = null
)

@Singleton
class EventBypassManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBypassRepository: EventBypassRepository
) {
    companion object {
        private const val WORK_NAME = "event_bypass_refresh"
    }

    private val _syncState = MutableStateFlow(EventBypassSyncState())
    val syncState = _syncState.asStateFlow()

    // The daily worker and the manual button can land at the same time; a second
    // walk over the mirrors would only duplicate traffic and flicker the UI.
    private val refreshMutex = Mutex()

    /**
     * Schedules the daily refresh. Privacy builds never phone home on their own, so
     * the job is cancelled there; the manual button on the bypass screen still works.
     */
    fun scheduleRefresh() {
        if (BuildConfig.IS_PRIVACY_BUILD) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }

        val request = PeriodicWorkRequest.Builder(
            EventBypassWorker::class.java,
            1L,
            TimeUnit.DAYS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    suspend fun refreshNow(): EventBypassResult = refreshMutex.withLock {
        _syncState.value = _syncState.value.copy(isRefreshing = true)
        try {
            val result = eventBypassRepository.refresh()
            _syncState.value = EventBypassSyncState(isRefreshing = false, lastResult = result)
            result
        } catch (e: Exception) {
            _syncState.value = EventBypassSyncState(
                isRefreshing = false,
                lastResult = EventBypassResult.UNREACHABLE
            )
            EventBypassResult.UNREACHABLE
        }
    }
}
