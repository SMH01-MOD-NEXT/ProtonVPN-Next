package ru.protonmod.next.data.network.warp

import org.junit.Assert.*
import org.junit.Test
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException

class WarpApiInterceptorTest {
    @Test fun `gate failure cannot reuse a pooled direct connection or send a request`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("direct")); server.start()
            lateinit var client: OkHttpClient
            var selected = false
            client = OkHttpClient.Builder().addInterceptor(WarpApiInterceptor({ throw IOException("no handshake") }, { selected }, { client })).build()
            val request = Request.Builder().url(server.url("/fixture")).build()
            client.newCall(request).execute().use { assertEquals("direct", it.body.string()) }
            selected = true
            try { client.newCall(request).execute().close(); fail("Must not bypass an unconfirmed WARP hop") }
            catch (_: IOException) { }
            assertEquals(1, server.requestCount)
            client.connectionPool.evictAll()
        }
    }
    @Test fun `ready requests use authenticated SOCKS remote DNS without local resolution`() {
        val listener = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val seen = worker.submit<String> {
                listener.accept().use { socket ->
                    socket.soTimeout = 3_000
                    val input = java.io.DataInputStream(socket.getInputStream())
                    val output = java.io.DataOutputStream(socket.getOutputStream())
                    assertEquals(5, input.readUnsignedByte())
                    repeat(input.readUnsignedByte()) { input.readByte() }
                    output.write(byteArrayOf(5, 2)); output.flush()
                    assertEquals(1, input.readUnsignedByte())
                    fun text(): String = ByteArray(input.readUnsignedByte()).also(input::readFully).decodeToString()
                    assertEquals("local-user", text()); assertEquals("local-secret", text())
                    output.write(byteArrayOf(1, 0)); output.flush()
                    assertEquals(5, input.readUnsignedByte()); assertEquals(1, input.readUnsignedByte()); input.readByte()
                    assertEquals(3, input.readUnsignedByte()); val host = text(); assertEquals(80, input.readUnsignedShort())
                    output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0)); output.flush()
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    output.writeBytes("HTTP/1.1 200 OK\r\nContent-Length: 6\r\nConnection: close\r\n\r\nvia-wg"); output.flush()
                    host
                }
            }
            lateinit var client: OkHttpClient
            client = OkHttpClient.Builder().dns { error("Local DNS must not run") }
                .addInterceptor(WarpApiInterceptor({ WarpApiProxy(listener.localPort, "local-user", "local-secret") }, { true }, { client })).build()
            client.newCall(Request.Builder().url("http://blocked.invalid/fixture").build()).execute().use {
                assertEquals("via-wg", it.body.string())
            }
            assertEquals("blocked.invalid", seen.get(5, java.util.concurrent.TimeUnit.SECONDS))
            client.connectionPool.evictAll()
        } finally { listener.close(); worker.shutdownNow() }
    }

}
