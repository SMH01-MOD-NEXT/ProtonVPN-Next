/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network.warp

import kotlinx.coroutines.*
import okhttp3.*
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import ru.protonmod.next.data.network.FixedSocks5SocketFactory
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Separate pool/transport prevents an already-pooled direct TLS connection
 * from escaping the handshake gate. URL, SNI, TLS and Proton pinning stay intact. */
internal class WarpApiInterceptor(
    private val awaitReady: (() -> Boolean) -> WarpApiProxy,
    private val selected: () -> Boolean,
    private val base: () -> OkHttpClient,
) : Interceptor {
    private val cancellationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var identity: WarpApiProxy? = null
    private var transport: OkHttpClient? = null

    @Synchronized private fun transport(proxy: WarpApiProxy): OkHttpClient {
        if (identity == proxy) return requireNotNull(transport)
        transport?.connectionPool?.evictAll()
        val builder = base().newBuilder()
        builder.interceptors().removeAll { it is WarpApiInterceptor }
        val result = builder.proxy(Proxy.NO_PROXY)
            .socketFactory(FixedSocks5SocketFactory("127.0.0.1", proxy.port, proxy.username, proxy.password))
            // Keep the domain label for SOCKS remote DNS; do not query local DoH
            // or reuse mirror-IP/SNI fallbacks outside the tunnel.
            .dns { host -> listOf(InetAddress.getByAddress(host, byteArrayOf(127, 0, 0, 1))) }
            .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
            .build()
        identity = proxy; transport = result
        return result
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!selected()) return chain.proceed(chain.request())
        val proxy = awaitReady { chain.call().isCanceled() }
        if (!selected()) throw java.io.IOException("WARP bypass changed")
        val nested = transport(proxy).newCall(chain.request())
        val originalTimeout = chain.call().timeout().timeoutNanos()
        if (originalTimeout > 0) nested.timeout().timeout(originalTimeout, TimeUnit.NANOSECONDS)
        val cancel = cancellationScope.launch {
            while (isActive) {
                if (chain.call().isCanceled()) { nested.cancel(); return@launch }
                delay(50)
            }
        }
        try {
            val response = nested.execute()
            val body = response.body
            val source = object : ForwardingSource(body.source()) {
                override fun close() { try { super.close() } finally { cancel.cancel() } }
                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    return try { super.read(sink, byteCount).also { if (it == -1L) cancel.cancel() } }
                    catch (error: Exception) { cancel.cancel(); throw error }
                }
            }.buffer()
            return response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source(): BufferedSource = source
            }).build()
        } catch (error: Exception) { cancel.cancel(); nested.cancel(); throw error }
    }
}
