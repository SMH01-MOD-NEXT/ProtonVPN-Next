/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.ProxyEndpoints
import ru.protonmod.next.vpn.ImportedWireGuard
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarpRepository @Inject constructor(private val client: OkHttpClient, private val settings: SettingsManager) {
    /** No Proton authenticator/interceptors: WARP must never receive Proton credentials. Retain
     * DNS and socket/proxy transports for direct/ByeDPI/custom proxy modes with standard TLS. */
    suspend fun generate(): String = withContext(Dispatchers.IO) {
        val private = X25519PrivateKeyParameters(SecureRandom())
        val b64 = Base64.getEncoder()
        val body = buildJsonObject {
            put("key", b64.encodeToString(private.generatePublicKey().encoded))
            put("install_id", ""); put("fcm_token", ""); put("tos", Instant.now().toString())
            put("type", "Android"); put("locale", "en_US"); put("warp_enabled", true)
        }
        val strategy = settings.getApiBypassStrategySync()
        val base = if (!settings.isApiBypassEnabledSync()) null else when (strategy) {
            SettingsManager.STRATEGY_NETLIFY -> ProxyEndpoints.NETLIFY_URL
            SettingsManager.STRATEGY_CLOUDFLARE -> ProxyEndpoints.CLOUDFLARE_URL
            SettingsManager.STRATEGY_DENO -> ProxyEndpoints.DENO_URL
            SettingsManager.STRATEGY_EVENT -> settings.getEventBypassUrlSync().also { require(ProxyEndpoints.httpsBase(it) != null) }
            SettingsManager.STRATEGY_BYEDPI, SettingsManager.STRATEGY_CUSTOM_PROXY -> null
            else -> error("Selected API bypass does not support WARP registration")
        }
        val routing = if (strategy == SettingsManager.STRATEGY_EVENT) {
            ru.protonmod.next.data.model.eventbypass.EventBypassCache.decode(settings.getEventBypassEventsSync())
                .firstOrNull { ProxyEndpoints.httpsBase(it.normalizedUrl()) == base?.let(ProxyEndpoints::httpsBase) }?.routing ?: "auto"
        } else "path"
        val url = if (base == null) "https://api.cloudflareclient.com/v0a2158/reg" else
            ProxyEndpoints.apiUrl("https://api.cloudflareclient.com/warp/reg".toHttpUrl(), requireNotNull(ProxyEndpoints.httpsBase(base)), routing).toString()
        val ssl = defaultSsl
        val builder = client.newBuilder().apply { interceptors().clear(); networkInterceptors().clear() }
            .authenticator(okhttp3.Authenticator.NONE)
            .certificatePinner(okhttp3.CertificatePinner.DEFAULT)
            .sslSocketFactory(ssl.first, ssl.second)
            .hostnameVerifier(javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier())
            .followRedirects(false).callTimeout(30, TimeUnit.SECONDS)
        // With bypass disabled, do not inherit a system proxy or a bypass socket factory.
        if (!settings.isApiBypassEnabledSync()) builder.proxy(java.net.Proxy.NO_PROXY).socketFactory(javax.net.SocketFactory.getDefault())
        val request = Request.Builder().url(url).header("User-Agent", "okhttp/4.12.0")
            .header("CF-Client-Version", "a-6.30-3596")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val payload = builder.build().newCall(request).execute().use { response ->
            check(response.isSuccessful) { "WARP registration failed (${response.code})" }
            val raw = response.body.string(); require(raw.length <= 65536)
            Json.parseToJsonElement(raw).jsonObject.getValue("config").jsonObject
        }
        val peer = payload.getValue("peers").jsonArray.first().jsonObject
        val addresses = payload.getValue("interface").jsonObject.getValue("addresses").jsonObject
        val reserved = Base64.getDecoder().decode(payload.getValue("client_id").jsonPrimitive.content)
        require(reserved.size == 3)
        val text = """
            [Interface]
            PrivateKey = ${b64.encodeToString(private.encoded)}
            Address = ${addresses.getValue("v4").jsonPrimitive.content}/32
            DNS = 1.1.1.1
            MTU = 1280
            [Peer]
            PublicKey = ${peer.getValue("public_key").jsonPrimitive.content}
            Endpoint = ${peer.getValue("endpoint").jsonObject.getValue("host").jsonPrimitive.content}
            AllowedIPs = 0.0.0.0/0
            PersistentKeepalive = 25
            Reserved = ${reserved.joinToString(", ") { (it.toInt() and 255).toString() }}
        """.trimIndent()
        ImportedWireGuard.encode(text) // Validate before exposing a generated configuration.
        text
    }
    private val defaultSsl: Pair<javax.net.ssl.SSLSocketFactory, javax.net.ssl.X509TrustManager>
        get() {
            val factory = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as java.security.KeyStore?)
            val trust = factory.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().single()
            val ssl = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            return ssl.socketFactory to trust
        }
}
