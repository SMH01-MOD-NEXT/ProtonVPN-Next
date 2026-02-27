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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.ServersCacheDao
import ru.protonmod.next.data.local.ServersCacheEntity
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.repository.VpnRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServersCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serversCacheDao: ServersCacheDao,
    private val serverDao: ServerDao,
    private val vpnRepository: VpnRepository,
    private val sessionDao: SessionDao
) {
    companion object {
        private const val TAG = "ServersCacheManager"
        private const val CACHE_DURATION_HOURS = 1L
        private const val CACHE_DURATION_MILLIS = CACHE_DURATION_HOURS * 60 * 60 * 1000
        private const val RETRY_DELAY_MINUTES = 2L
        private const val REQUEST_TIMEOUT_SECONDS = 15L
        private const val BACKGROUND_UPDATE_WORK_TAG = "servers_bg_update"
        private const val BACKGROUND_UPDATE_WORK_NAME = "servers_update_work"
        private const val AUTO_UPDATE_INTERVAL_MINUTES = 2L
    }

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var autoUpdateJob: Job? = null

    /**
     * Starts a periodic background update that runs while the app process is alive.
     * Updates server loads every 2 minutes.
     */
    fun startAutoUpdate() {
        if (autoUpdateJob?.isActive == true) return
        
        autoUpdateJob = managerScope.launch {
            Log.d(TAG, "Starting auto-update loop (every $AUTO_UPDATE_INTERVAL_MINUTES minutes)")
            while (isActive) {
                val session = sessionDao.getSession()
                if (session != null) {
                    Log.d(TAG, "Auto-update: triggering server fetch...")
                    getServers(
                        session.accessToken,
                        session.sessionId,
                        session.userTier,
                        forceRefresh = true
                    )
                } else {
                    Log.d(TAG, "Auto-update: no active session found")
                }
                delay(TimeUnit.MINUTES.toMillis(AUTO_UPDATE_INTERVAL_MINUTES))
            }
        }
    }

    fun stopAutoUpdate() {
        autoUpdateJob?.cancel()
        autoUpdateJob = null
        Log.d(TAG, "Auto-update loop stopped")
    }

    /**
     * Returns a Flow of servers that ViewModels can observe for real-time updates.
     */
    fun getServersFlow(): Flow<List<ru.protonmod.next.data.network.LogicalServer>> {
        return serverDao.getServersFlow().map { entities ->
            entities.map { ru.protonmod.next.data.local.ServerMapper.toDomain(it) }
        }
    }

    suspend fun getServers(
        accessToken: String,
        sessionId: String,
        userTier: Int,
        forceRefresh: Boolean = false
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val cacheInfo = serversCacheDao.getCacheInfo()

            val shouldFetch = forceRefresh || cacheInfo == null || now > cacheInfo.expiresAt
            Log.d(TAG, "getServers: userTier=$userTier, forceRefresh=$forceRefresh, hasCache=${cacheInfo != null}, expired=${if (cacheInfo != null) now > cacheInfo.expiresAt else "N/A"}")

            if (shouldFetch) {
                Log.d(TAG, "Fetching servers from API for tier $userTier...")
                
                val result = vpnRepository.getServersWithTimeout(accessToken, sessionId, REQUEST_TIMEOUT_SECONDS, userTier = userTier)

                result.onSuccess { servers ->
                    Log.d(TAG, "Servers fetched successfully: ${servers.size} logical servers")
                    
                    val entities = servers.map { server ->
                        ru.protonmod.next.data.local.ServerMapper.toEntity(server)
                    }
                    
                    val sample = servers.firstOrNull()
                    Log.d(TAG, "Sample server load before saving: ${sample?.name} -> ${sample?.averageLoad}")

                    serverDao.insertServers(entities)

                    val newCacheInfo = ServersCacheEntity(
                        cachedAt = now,
                        expiresAt = now + CACHE_DURATION_MILLIS
                    )
                    serversCacheDao.saveCacheInfo(newCacheInfo)
                    Log.d(TAG, "Cache updated successfully")
                }

                result.onFailure { error ->
                    Log.e(TAG, "Failed to fetch servers from API", error)
                    if (cacheInfo != null) {
                        Log.d(TAG, "Using stale cache, scheduling retry")
                        scheduleBackgroundUpdate(accessToken, sessionId, userTier, RETRY_DELAY_MINUTES)
                    } else {
                        return Result.failure(error)
                    }
                }
            } else {
                Log.d(TAG, "Using valid cache (expires in ${(cacheInfo!!.expiresAt - now) / 1000}s)")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getServers", e)
            Result.failure(e)
        }
    }


    suspend fun getCachedServers(): List<ru.protonmod.next.data.network.LogicalServer> {
        return try {
            val servers = serverDao.getAllServers().map { entity ->
                ru.protonmod.next.data.local.ServerMapper.toDomain(entity)
            }
            Log.d(TAG, "Retrieved ${servers.size} servers from DB")
            servers
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
        userTier: Int,
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
                "session_id" to sessionId,
                "user_tier" to userTier
            ))
        }.build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BACKGROUND_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            updateRequest
        )

        Log.d(TAG, "Background update scheduled with userTier=$userTier")
    }

    fun cancelBackgroundUpdate() {
        WorkManager.getInstance(context).cancelAllWorkByTag(BACKGROUND_UPDATE_WORK_TAG)
        Log.d(TAG, "Background update cancelled")
    }
}
