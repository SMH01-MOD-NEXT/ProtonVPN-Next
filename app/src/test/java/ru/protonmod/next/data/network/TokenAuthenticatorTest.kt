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

import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import ru.protonmod.next.data.local.SessionEntity

class TokenAuthenticatorTest {

    @Mock
    private lateinit var sessionManager: SessionManager

    private lateinit var authenticator: TokenAuthenticator

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        authenticator = TokenAuthenticator(sessionManager)
    }

    @Test
    fun `authenticate returns null when no session found`() {
        runBlocking {
            whenever(sessionManager.getSession()).thenReturn(null)
        }

        val response = mockResponse("https://vpn-api.proton.me/vpn/v2/logicals")
        val result = authenticator.authenticate(null, response)

        assertNull(result)
    }

    @Test
    fun `authenticate force refreshes token and retries request`() {
        val (oldSession, updatedSession) = sessions()
        stubSuccessfulRefresh(oldSession, updatedSession)

        val response = mockResponse(
            "https://vpn-api.proton.me/vpn/v2/logicals",
            "Bearer old_token"
        )
        val result = authenticator.authenticate(null, response)

        assertEquals("Bearer new_token", result?.header("Authorization"))
        runBlocking {
            verify(sessionManager).refreshSession(oldSession, force = true)
        }
    }

    @Test
    fun `authenticate refreshes token for Deno bypass`() {
        val (oldSession, updatedSession) = sessions()
        stubSuccessfulRefresh(oldSession, updatedSession)

        val response = mockResponse(
            "https://protonvpn-next-web--main.smh01-mirrors.deno.net/api/vpn/v2/logicals",
            "Bearer old_token"
        )
        val result = authenticator.authenticate(null, response)

        assertEquals("Bearer new_token", result?.header("Authorization"))
    }

    @Test
    fun `authenticate refreshes token for marked rotating event host`() {
        val (oldSession, updatedSession) = sessions()
        stubSuccessfulRefresh(oldSession, updatedSession)

        val response = mockResponse(
            "https://temporary-event.example/api/vpn/v2/logicals",
            "Bearer old_token",
            markAsProtonApi = true
        )
        val result = authenticator.authenticate(null, response)

        assertEquals("Bearer new_token", result?.header("Authorization"))
    }

    @Test
    fun `authenticate ignores unmarked unknown host`() {
        val response = mockResponse(
            "https://temporary-event.example/api/vpn/v2/logicals",
            "Bearer old_token"
        )

        assertNull(authenticator.authenticate(null, response))
        verifyNoInteractions(sessionManager)
    }

    private fun stubSuccessfulRefresh(
        oldSession: SessionEntity,
        updatedSession: SessionEntity
    ) {
        runBlocking {
            whenever(sessionManager.getSession()).thenReturn(oldSession)
            whenever(sessionManager.refreshSession(any(), eq(true)))
                .thenReturn(Result.success(updatedSession))
        }
    }

    private fun sessions(): Pair<SessionEntity, SessionEntity> {
        val oldSession = SessionEntity(
            accessToken = "old_token",
            refreshToken = "refresh_token",
            sessionId = "session_id",
            userId = "user_id"
        )
        return oldSession to oldSession.copy(
            accessToken = "new_token",
            refreshToken = "new_refresh_token"
        )
    }

    private fun mockResponse(
        url: String,
        authHeader: String? = null,
        markAsProtonApi: Boolean = false
    ): Response {
        val requestBuilder = Request.Builder().url(url)
        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader)
        }
        if (markAsProtonApi) {
            requestBuilder.tag(ProtonApiRequestTag::class.java, ProtonApiRequestTag)
        }
        val request = requestBuilder.build()

        return mock<Response>().apply {
            whenever(this.request).thenReturn(request)
            whenever(this.code).thenReturn(401)
        }
    }
}
