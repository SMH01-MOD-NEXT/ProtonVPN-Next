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

package ru.protonmod.next.data.ai

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.FixedSocks5SocketFactory
import ru.protonmod.next.utils.ProtonLogger
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal data class AiProxyConfig(
    val type: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)

internal fun selectAiProxyConfig(
    useBypass: Boolean,
    strategy: String,
    type: String,
    host: String,
    port: Int,
    username: String,
    password: String
): AiProxyConfig? {
    if (!useBypass || strategy != SettingsManager.STRATEGY_CUSTOM_PROXY) return null
    val normalizedHost = host.trim()
    if (normalizedHost.isEmpty() || port !in 1..65535) return null
    if (type != SettingsManager.PROXY_TYPE_SOCKS && type != SettingsManager.PROXY_TYPE_HTTP) return null
    return AiProxyConfig(type, normalizedHost, port, username, password)
}

@Singleton
class AiClient @Inject constructor(
    private val baseOkHttpClient: OkHttpClient,
    private val settingsManager: SettingsManager
) {
    @Volatile private var proxyClient: Pair<AiProxyConfig, OkHttpClient>? = null

    private fun getClient(useBypass: Boolean): OkHttpClient {
        val config = selectAiProxyConfig(
            useBypass = useBypass,
            strategy = settingsManager.getApiBypassStrategySync(),
            type = settingsManager.getApiProxyTypeSync(),
            host = settingsManager.getApiProxyHostSync(),
            port = settingsManager.getApiProxyPortSync(),
            username = settingsManager.getApiProxyUsernameSync(),
            password = settingsManager.getApiProxyPasswordSync()
        ) ?: return baseOkHttpClient

        proxyClient?.takeIf { it.first == config }?.let { return it.second }
        return synchronized(this) {
            proxyClient?.takeIf { it.first == config }?.second
                ?: buildProxyClient(config).also { proxyClient = config to it }
        }
    }

    private fun buildProxyClient(config: AiProxyConfig): OkHttpClient {
        val builder = baseOkHttpClient.newBuilder()
        return if (config.type == SettingsManager.PROXY_TYPE_SOCKS) {
            // SOCKS5 resolves the AI endpoint on the proxy side, avoiding both local DNS
            // filtering and exposing a Russian source IP to region-gated providers.
            builder
                .proxy(Proxy.NO_PROXY)
                .dns { hostname ->
                    listOf(InetAddress.getByAddress(hostname, byteArrayOf(127, 0, 0, 1)))
                }
                .socketFactory(
                    FixedSocks5SocketFactory(
                        config.host,
                        config.port,
                        config.username,
                        config.password
                    )
                )
                .build()
        } else {
            builder
                .proxy(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress.createUnresolved(config.host, config.port)
                    )
                )
                .proxyAuthenticator { _, response ->
                    if (config.username.isBlank() || response.request.header("Proxy-Authorization") != null) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
                            .build()
                    }
                }
                .build()
        }
    }

    suspend fun query(
        provider: AiProvider,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userQuery: String,
        useBypass: Boolean = true
    ): String? {
        if (apiKey.isBlank()) return null
        
        val actualModel = if (model.isBlank()) provider.getDefaultModel() else model
        val client = getClient(useBypass)

        return when (provider) {
            AiProvider.OPENAI, AiProvider.DEEPSEEK -> queryOpenAiCompatible(client, provider, actualModel, apiKey, systemPrompt, userQuery)
            AiProvider.GEMINI -> queryGemini(client, actualModel, apiKey, systemPrompt, userQuery)
            AiProvider.ANTHROPIC -> queryAnthropic(client, actualModel, apiKey, systemPrompt, userQuery)
        }
    }

    private suspend fun queryOpenAiCompatible(
        client: OkHttpClient,
        provider: AiProvider,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userQuery: String
    ): String? {
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userQuery)
                })
            })
            put("response_format", JSONObject().apply { put("type", "json_object") })
        }

        val request = Request.Builder()
            .url(provider.baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(client, request) { responseBody ->
            val root = JSONObject(responseBody)
            root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    private suspend fun queryGemini(
        client: OkHttpClient,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userQuery: String
    ): String? {
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "System: $systemPrompt\n\nUser: $userQuery")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val url = "${AiProvider.GEMINI.baseUrl}$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(client, request) { responseBody ->
            val root = JSONObject(responseBody)
            root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
    }

    private suspend fun queryAnthropic(
        client: OkHttpClient,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userQuery: String
    ): String? {
        val json = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("system", systemPrompt)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userQuery)
                })
            })
        }

        val request = Request.Builder()
            .url(AiProvider.ANTHROPIC.baseUrl)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(client, request) { responseBody ->
            val root = JSONObject(responseBody)
            root.getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    private suspend fun executeRequest(client: OkHttpClient, request: Request, parser: (String) -> String): String? =
        suspendCancellableCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    ProtonLogger.e("AiClient", "Request failed", e)
                    continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            val errorBody = it.body.string()
                            ProtonLogger.e("AiClient", "Unsuccessful response: ${it.code} ${it.message}\n$errorBody")
                            continuation.resume(null)
                            return
                        }
                        val body = it.body.string()
                        try {
                            continuation.resume(parser(body))
                        } catch (e: Exception) {
                            ProtonLogger.e("AiClient", "Failed to parse response", e)
                            continuation.resume(null)
                        }
                    }
                }
            })
        }
}
