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

package ru.protonmod.next.data.cache

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServersCacheDao
import ru.protonmod.next.data.local.ServersCacheEntity
import ru.protonmod.next.data.local.ServerMapper
import ru.protonmod.next.data.repository.VpnRepository

@HiltWorker
class ServersCacheUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val vpnRepository: VpnRepository,
    private val serverDao: ServerDao,
    private val serversCacheDao: ServersCacheDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ServersCacheWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val accessToken = inputData.getString("access_token") ?: return@withContext Result.failure()
            val sessionId = inputData.getString("session_id") ?: return@withContext Result.failure()
            val userTier = inputData.getInt("user_tier", 0)

            Log.d(TAG, "Background update worker started for tier $userTier")

            val result = vpnRepository.getServersWithTimeout(accessToken, sessionId, 15L, userTier = userTier)

            result.onSuccess { servers ->
                Log.d(TAG, "Background update success: ${servers.size} servers")
                serverDao.insertServers(servers.map { ServerMapper.toEntity(it) })
                val now = System.currentTimeMillis()
                serversCacheDao.saveCacheInfo(ServersCacheEntity(cachedAt = now, expiresAt = now + 3600000L))
                return@withContext Result.success()
            }

            result.onFailure {
                Log.e(TAG, "Background update failed: ${it.message}")
                return@withContext Result.retry()
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
            Result.retry()
        }
    }
}
