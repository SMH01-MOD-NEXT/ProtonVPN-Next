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
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServersCacheDao
import ru.protonmod.next.data.local.ServersCacheEntity
import ru.protonmod.next.data.repository.VpnRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServersCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serversCacheDao: ServersCacheDao,
    private val serverDao: ServerDao,
    private val vpnRepository: VpnRepository
) {
    companion object {
        private const val TAG = "ServersCacheManager"
        private const val CACHE_DURATION_HOURS = 1L
        private const val CACHE_DURATION_MILLIS = CACHE_DURATION_HOURS * 60 * 60 * 1000
        private const val RETRY_DELAY_MINUTES = 2L
        private const val REQUEST_TIMEOUT_SECONDS = 10L
        private const val BACKGROUND_UPDATE_WORK_TAG = "servers_bg_update"
        private const val BACKGROUND_UPDATE_WORK_NAME = "servers_update_work"
    }

    suspend fun getServers(
        accessToken: String,
        sessionId: String,
        forceRefresh: Boolean = false
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val cacheInfo = serversCacheDao.getCacheInfo()

            val shouldFetch = forceRefresh || cacheInfo == null || now > cacheInfo.expiresAt

            if (shouldFetch) {
                val result: Result<List<ru.protonmod.next.data.network.LogicalServer>> =
                    vpnRepository.getServersWithTimeout(accessToken, sessionId, REQUEST_TIMEOUT_SECONDS)

                result.onSuccess { servers ->
                    Log.d(TAG, "Servers fetched successfully, caching...")
                    serverDao.insertServers(servers.map { server ->
                        ru.protonmod.next.data.local.ServerMapper.toEntity(server)
                    })

                    val newCacheInfo = ServersCacheEntity(
                        cachedAt = now,
                        expiresAt = now + CACHE_DURATION_MILLIS
                    )
                    serversCacheDao.saveCacheInfo(newCacheInfo)
                    Log.d(TAG, "Cache updated, expires in ${CACHE_DURATION_HOURS} hour(s)")
                }

                result.onFailure { error ->
                    Log.e(TAG, "Failed to fetch servers: ${error.message}")
                    if (cacheInfo != null) {
                        Log.d(TAG, "Using stale cache, scheduling retry in ${RETRY_DELAY_MINUTES} minutes")
                        scheduleBackgroundUpdate(accessToken, sessionId, RETRY_DELAY_MINUTES)
                    } else {
                        throw error
                    }
                }
            } else {
                val timeUntilExpiry = cacheInfo.expiresAt - now
                if (timeUntilExpiry < 5 * 60 * 1000) { // Менее 5 минут до истечения
                    Log.d(TAG, "Cache expiring soon, scheduling background update")
                    scheduleBackgroundUpdate(accessToken, sessionId, 0L)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getServers", e)
            Result.failure(e)
        }
    }


    suspend fun getCachedServers(): List<ru.protonmod.next.data.network.LogicalServer> {
        return try {
            serverDao.getAllServers().map { entity ->
                ru.protonmod.next.data.local.ServerMapper.toDomain(entity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving cached servers", e)
            emptyList()
        }
    }

    suspend fun isCacheExpired(): Boolean {
        val cacheInfo = serversCacheDao.getCacheInfo()
        if (cacheInfo == null) return true
        return System.currentTimeMillis() > cacheInfo.expiresAt
    }

    suspend fun getCacheTimestamp(): Long? {
        return serversCacheDao.getCacheInfo()?.cachedAt
    }

    private fun scheduleBackgroundUpdate(
        accessToken: String,
        sessionId: String,
        delayMinutes: Long = 0L
    ) {
        val updateRequest = PeriodicWorkRequestBuilder<ru.protonmod.next.data.cache.ServersCacheUpdateWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        ).apply {
            if (delayMinutes > 0) {
                setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            }
            addTag(BACKGROUND_UPDATE_WORK_TAG)
            setInputData(workDataOf(
                "access_token" to accessToken,
                "session_id" to sessionId
            ))
        }.build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BACKGROUND_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            updateRequest
        )

        Log.d(TAG, "Background update scheduled with delay: ${delayMinutes}min")
    }

    fun cancelBackgroundUpdate() {
        WorkManager.getInstance(context).cancelAllWorkByTag(BACKGROUND_UPDATE_WORK_TAG)
        Log.d(TAG, "Background update cancelled")
    }
}

