/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network
import org.junit.Assert.*
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl

class ProxyEndpointsTest {
    @Test fun `event Vercel API path is carried in query and original query survives`() {
        val original = "https://api.protonvpn.ch/auth/v4?mode=a%20b".toHttpUrl()
        val result = ProxyEndpoints.apiUrl(original, "https://dynamic.vercel.app/api/".toHttpUrl())
        assertEquals("/api", result.encodedPath)
        assertEquals("/auth/v4", result.queryParameter("__path"))
        assertEquals("a b", result.queryParameter("mode"))
    }
    @Test fun `path routing keeps prefixes and encoded paths once`() {
        val result = ProxyEndpoints.apiUrl("https://api.protonvpn.ch/auth/v4/a%2Fb?q=x".toHttpUrl(), ProxyEndpoints.DENO_URL.toHttpUrl(), "path")
        assertEquals("/api/auth/v4/a%2Fb", result.encodedPath)
        assertEquals("x", result.queryParameter("q"))
    }
    @Test fun `explicit query routing works for a custom hostname and verification`() {
        val result = ProxyEndpoints.apiUrl("https://verify-api.proton.me/captcha/v1".toHttpUrl(), "https://custom.example/api/".toHttpUrl(), "query")
        assertEquals("/verify-api/captcha/v1", result.queryParameter("__path"))
        assertEquals("/api", result.encodedPath)
    }
    @Test fun `invalid credentials fragments and insecure URLs are refused`() {
        for (url in listOf("", "https://", "garbage", "http://example.com", "https://user:pass@example.com", "https://example.com/#token"))
            assertNull(url, ProxyEndpoints.httpsBase(url))
    }
    @Test fun `echo fallbacks never leave the permitted host`() {
        for (base in listOf(ProxyEndpoints.DENO_URL, "https://dynamic.vercel.app/api/", "https://custom.example/proxy/")) {
            val parsed = base.toHttpUrl()
            assertTrue(ProxyEndpoints.echoUrls(parsed).all { it.toHttpUrl().host == parsed.host })
        }
    }
}
