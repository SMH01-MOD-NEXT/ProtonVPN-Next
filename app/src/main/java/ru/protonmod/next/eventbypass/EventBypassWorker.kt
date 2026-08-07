package ru.protonmod.next.eventbypass

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.protonmod.next.data.repository.EventBypassResult
import ru.protonmod.next.utils.ProtonLogger

@HiltWorker
class EventBypassWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventBypassManager: EventBypassManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            when (eventBypassManager.refreshNow()) {
                EventBypassResult.UPDATED,
                EventBypassResult.NOT_CONFIGURED -> Result.success()

                // Offline, a third-party VPN in the way or every mirror down are all
                // temporary states, so let WorkManager back off and try again.
                EventBypassResult.BLOCKED_OFFLINE,
                EventBypassResult.BLOCKED_VPN,
                EventBypassResult.UNREACHABLE -> Result.retry()
            }
        } catch (e: Exception) {
            ProtonLogger.e("EventBypassWorker", "Event bypass refresh failed", e)
            Result.retry()
        }
    }
}
