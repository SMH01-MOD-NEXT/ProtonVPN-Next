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

package ru.protonmod.next.data.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val authApiProvider: Provider<ProtonAuthApi>
) {
    companion object {
        private const val TAG = "SessionManager"
        private const val REFRESH_DEBOUNCE_MS = 60000L // 1 minute
    }

    private val refreshMutex = Mutex()
    private var lastRefreshTime = 0L

    /**
     * Refreshes the session using the provided [session].
     * If a refresh is already in progress, it waits for it and returns the updated session.
     */
    suspend fun refreshSession(session: SessionEntity): Result<SessionEntity> =
        refreshSession(session, force = false)

    /**
     * Refreshes the session, optionally bypassing the time debounce after the API explicitly
     * rejects the current token.
     */
    suspend fun refreshSession(
        session: SessionEntity,
        force: Boolean
    ): Result<SessionEntity> = refreshMutex.withLock {
        val currentTime = System.currentTimeMillis()
        val recentlyRefreshed = currentTime - lastRefreshTime < REFRESH_DEBOUNCE_MS

        // Check if session was already updated by another thread while waiting for lock
        val currentSession = sessionDao.getSession()
        if (currentSession != null && currentSession.accessToken != session.accessToken) {
            ProtonLogger.i(TAG, "Session was already refreshed by a parallel request.")
            return Result.success(currentSession)
        }

        if (recentlyRefreshed && !force) {
            ProtonLogger.i(TAG, "Refresh debounced, returning current session.")
            return currentSession?.let { Result.success(it) } ?: Result.failure(Exception("No session available"))
        }

        ProtonLogger.i(TAG, "Refreshing session (UID: ${session.sessionId})")
        
        return try {
            val refreshRequest = RefreshSessionRequest(
                uid = session.sessionId,
                refreshToken = session.refreshToken
            )
            val response = authApiProvider.get().refreshSession(refreshRequest)

            if (response.code == 1000 && response.accessToken != null) {
                lastRefreshTime = System.currentTimeMillis()
                
                val updatedSession = session.copy(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken ?: session.refreshToken
                )
                sessionDao.saveSession(updatedSession)
                ProtonLogger.i(TAG, "Session refreshed successfully.")
                Result.success(updatedSession)
            } else {
                ProtonLogger.e(TAG, "Refresh failed with code ${response.code}")
                Result.failure(Exception("Refresh failed with code ${response.code}"))
            }
        } catch (e: Exception) {
            ProtonLogger.e(TAG, "Exception during session refresh", e)
            Result.failure(e)
        }
    }

    suspend fun getSession(): SessionEntity? = sessionDao.getSession()
}
