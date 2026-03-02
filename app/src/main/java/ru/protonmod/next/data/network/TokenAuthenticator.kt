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

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import ru.protonmod.next.data.local.SessionDao
import javax.inject.Inject
import javax.inject.Provider

class TokenAuthenticator @Inject constructor(
    private val sessionDao: SessionDao,
    private val authApiProvider: Provider<ProtonAuthApi>
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val requestUrl = response.request.url.toString()
        Log.d(TAG, "authenticate() triggered for URL: $requestUrl (HTTP ${response.code})")

        // Prevent infinite loops if the new token also returns 401 Unauthorized
        if (response.responseCount >= 3) {
            Log.e(TAG, "Failed to refresh token after 3 attempts for $requestUrl. Giving up to prevent infinite loop.")
            return null
        }

        return synchronized(this) {
            Log.d(TAG, "Entering synchronized block to refresh token")

            // Read the current session synchronously
            val session = runBlocking { sessionDao.getSession() }
            if (session == null || session.refreshToken.isNullOrEmpty() || session.sessionId.isNullOrEmpty()) {
                Log.e(TAG, "Cannot refresh token: Session is null or missing refreshToken/sessionId in DB")
                return null
            }

            // Check if the request's auth header matches the current token.
            // If it doesn't, another thread might have already refreshed the token while we were waiting for the lock.
            val requestHeader = response.request.header("Authorization")
            if (requestHeader != null && !requestHeader.contains(session.accessToken)) {
                Log.d(TAG, "Token was already refreshed by another thread. Retrying original request with the new token.")
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .build()
            }

            // Token is genuinely expired, we need to refresh it
            try {
                Log.d(TAG, "Attempting to call refreshSession API with UID: ${session.sessionId}")

                val authApi = authApiProvider.get()
                val refreshRequest = RefreshSessionRequest(
                    uid = session.sessionId,
                    refreshToken = session.refreshToken
                )

                // Execute the refresh token request synchronously
                val refreshResponse = runBlocking {
                    authApi.refreshSession(refreshRequest)
                }

                Log.d(TAG, "Refresh API response received. Code: ${refreshResponse.code}")

                if (refreshResponse.code == 1000 && refreshResponse.accessToken != null) {
                    Log.d(TAG, "Successfully acquired new access token. Updating database...")

                    // Update session with new tokens
                    val newAccessToken = refreshResponse.accessToken
                    val newRefreshToken = refreshResponse.refreshToken ?: session.refreshToken

                    val updatedSession = session.copy(
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken
                    )

                    runBlocking { sessionDao.saveSession(updatedSession) }
                    Log.d(TAG, "Database updated with new session. Retrying the original request ($requestUrl).")

                    // Retry the failed request with the new access token
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                } else {
                    Log.e(TAG, "Refresh API returned unexpected code (${refreshResponse.code}) or null access token.")
                }
            } catch (e: Exception) {
                // Refresh failed (e.g., network error or refresh token itself is expired)
                Log.e(TAG, "Exception occurred during token refresh: ${e.message}", e)
            }

            Log.e(TAG, "Returning null from Authenticator. The 401/402 error will be passed down to the caller.")
            null
        }
    }

    /**
     * Helper extension to count how many times this request has been retried
     */
    private val Response.responseCount: Int
        get() {
            var result = 1
            var priorResponse = priorResponse
            while (priorResponse != null) {
                result++
                priorResponse = priorResponse.priorResponse
            }
            return result
        }
}