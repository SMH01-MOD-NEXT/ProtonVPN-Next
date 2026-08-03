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
        provider: AiProviderConfig,
        model: String,
        apiKey: String,
        systemPrompt: String,
        userQuery: String,
        useBypass: Boolean = true
    ): String? {
        if (apiKey.isBlank()) return null

        val actualModel = model.ifBlank { provider.getDefaultModel() }
        if (actualModel.isBlank()) {
            ProtonLogger.e("AiClient", "No model configured for provider ${provider.id}")
            return null
        }
        val client = getClient(useBypass)

        return when (provider.format) {
            AiApiFormat.OPENAI -> queryOpenAiCompatible(client, provider, actualModel, apiKey, systemPrompt, userQuery)
            AiApiFormat.GEMINI -> queryGemini(client, provider, actualModel, apiKey, systemPrompt, userQuery)
            AiApiFormat.ANTHROPIC -> queryAnthropic(client, provider, actualModel, apiKey, systemPrompt, userQuery)
        }
    }

    /**
     * Lists the models a provider exposes, mirroring what IDEs do, so users do not have to type
     * model names by hand.
     *
     * @return the discovered model ids, or null when the catalogue could not be fetched.
     */
    suspend fun listModels(
        provider: AiProviderConfig,
        apiKey: String,
        useBypass: Boolean = true
    ): List<String>? {
        if (apiKey.isBlank()) return null
        val request = Request.Builder()
            .url(AiEndpoints.models(provider, apiKey))
            .apply {
                when (provider.format) {
                    AiApiFormat.OPENAI -> addHeader("Authorization", "Bearer $apiKey")
                    AiApiFormat.ANTHROPIC -> {
                        addHeader("x-api-key", apiKey)
                        addHeader("anthropic-version", ANTHROPIC_VERSION)
                    }
                    AiApiFormat.GEMINI -> Unit // The key travels as a query parameter.
                }
            }
            .get()
            .build()

        val body = executeRequest(client = getClient(useBypass), request = request) { it } ?: return null
        return AiModelListParser.parse(provider.format, body).takeIf(List<String>::isNotEmpty)
    }

    private suspend fun queryOpenAiCompatible(
        client: OkHttpClient,
        provider: AiProviderConfig,
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
            .url(AiEndpoints.chat(provider, model, apiKey))
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
        provider: AiProviderConfig,
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

        val request = Request.Builder()
            .url(AiEndpoints.chat(provider, model, apiKey))
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(client, request) { responseBody ->
            val root = JSONObject(responseBody)
            root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
    }

    private suspend fun queryAnthropic(
        client: OkHttpClient,
        provider: AiProviderConfig,
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
            .url(AiEndpoints.chat(provider, model, apiKey))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
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
                            // Rejections from a third-party AI provider (bad key, spent quota,
                            // rate limit, insufficient balance) are the provider account's
                            // problem, not a defect in this app, so they are logged as warnings
                            // and never reported as errors (ANDROID-234).
                            ProtonLogger.w("AiClient", "Unsuccessful response: ${it.code} ${it.message}\n$errorBody")
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

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
