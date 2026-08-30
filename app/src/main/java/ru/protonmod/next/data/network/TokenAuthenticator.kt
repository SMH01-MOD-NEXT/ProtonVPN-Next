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
import javax.inject.Inject

/** Marker retained on rewritten requests so rotating event-proxy hosts remain authenticated. */
internal object ProtonApiRequestTag

class TokenAuthenticator @Inject constructor(
    private val sessionManager: SessionManager
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val requestUrl = response.request.url
        val requestHost = requestUrl.host
        
        // Only authenticate requests that were classified as Proton API traffic before
        // URL rewriting, or requests to a known Proton/proxy host. The marker is needed
        // for the rotating Event bypass host and avoids duplicating its mutable URL here.
        val isProtonApi = response.request.tag(ProtonApiRequestTag::class.java) === ProtonApiRequestTag ||
            requestHost == "vpn-api.proton.me" ||
            requestHost.endsWith(".proton.me") ||
            requestHost.endsWith(".protonmail.ch") ||
            requestHost.endsWith(".protonvpn.ch") ||
            requestHost.endsWith(".protonvpn.com") ||
            requestHost.endsWith(".protonmail.com") ||
            requestHost == "shimmering-stroopwafel-51675e.netlify.app" ||
            requestHost == "protonvpn-next-web.smh01.workers.dev" ||
            requestHost == "protonvpn-next-web--main.smh01-mirrors.deno.net"
        
        // Do not attempt to authenticate if it's already an authentication or refresh request
        val isAuthRequest = requestUrl.encodedPath.contains("auth/v4")
        
        if (!isProtonApi || isAuthRequest) {
            return null
        }

        ProtonLogger.i(TAG, "HTTP 401 detected for $requestUrl. Initializing token refresh cycle.")
        ProtonLogger.addSentryBreadcrumb(TAG, "Auth Step: Token Expired ($requestUrl)", "WARNING", "auth.token")

        // A failed request may be retried once. If the replacement token is also rejected,
        // return the second 401 instead of refreshing indefinitely.
        if (response.responseCount >= 2) {
            ProtonLogger.e(TAG, "Aborting refresh cycle: replacement token was also rejected for $requestUrl.")
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
            sessionManager.refreshSession(session, force = true)
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