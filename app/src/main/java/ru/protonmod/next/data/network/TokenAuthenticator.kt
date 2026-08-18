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

import ru.protonmod.next.utils.ProtonLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Provider

class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val requestUrl = response.request.url
        val requestHost = requestUrl.host
        
        // Only authenticate for Proton API requests.
        // These are hardcoded for safety as this is sensitive logic.
        val isProtonApi = requestHost == "vpn-api.proton.me" || 
                          requestHost == "shimmering-stroopwafel-51675e.netlify.app" ||
                          requestHost == "protonvpn-next-web.smh01.workers.dev"
        
        // Do not attempt to authenticate if it's already an authentication or refresh request
        val isAuthRequest = requestUrl.encodedPath.contains("auth/v4")
        
        if (!isProtonApi || isAuthRequest) {
            return null
        }

        ProtonLogger.i(TAG, "HTTP 401 detected for $requestUrl. Initializing token refresh cycle.")
        ProtonLogger.addSentryBreadcrumb(TAG, "Auth Step: Token Expired ($requestUrl)", "WARNING", "auth.token")

        // Prevent infinite loops if the new token also returns 401 Unauthorized
        if (response.responseCount >= 3) {
            ProtonLogger.e(TAG, "Aborting refresh cycle: Max retry count (3) exceeded for $requestUrl. Session might be completely broken.")
            return null
        }

        // Read the current session synchronously
        val session = runBlocking(Dispatchers.IO) { sessionManager.getSession() }
        if (session == null || session.refreshToken.isNullOrEmpty() || session.sessionId.isNullOrEmpty()) {
            ProtonLogger.e(TAG, "Token refresh failed: No valid session found in DB")
            return null
        }

        // Check if the request's auth header matches the current token.
        val requestHeader = response.request.header("Authorization")
        if (requestHeader != null && !requestHeader.contains(session.accessToken)) {
            ProtonLogger.i(TAG, "Token was already refreshed by a parallel request. Retrying with current token.")
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()
        }

        // Token is genuinely expired, we need to refresh it
        val refreshResult = runBlocking(Dispatchers.IO) {
            sessionManager.refreshSession(session)
        }

        return if (refreshResult.isSuccess) {
            val updatedSession = refreshResult.getOrNull()!!
            ProtonLogger.i(TAG, "Successfully acquired new access token.")
            ProtonLogger.addSentryBreadcrumb(TAG, "Auth Step: Token Refreshed", "INFO", "auth.token")

            // Retry the failed request with the new access token
            response.request.newBuilder()
                .header("Authorization", "Bearer ${updatedSession.accessToken}")
                .build()
        } else {
            // Expected whenever a refresh token is revoked or expired: the caller re-authenticates.
            ProtonLogger.w(TAG, "Refresh request failed. Passing 401 to caller.")
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