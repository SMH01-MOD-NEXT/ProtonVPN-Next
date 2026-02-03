/*
 *
 * * Copyright (c) 2025. Proton AG
 * *
 * * This file is part of ProtonVPN.
 * *
 * * ProtonVPN is free software: you can redistribute it and/or modify
 * * it under the terms of the GNU General Public License as published by
 * * the Free Software Foundation, either version 3 of the License, or
 * * (at your option) any later version.
 * *
 * * ProtonVPN is distributed in the hope that it will be useful,
 * * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * * GNU General Public License for more details.
 * *
 * * You should have received a copy of the GNU General Public License
 * * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.proxy

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Manages the lifecycle of the Xray binary and the local SOCKS5 proxy.
 */
class VlessManager private constructor(private val context: Context) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null
    private var xrayProcess: Process? = null
    private var outReaderJob: Job? = null
    private var errReaderJob: Job? = null

    // --- State Properties ---
    @Volatile private var isRunning = false
    @Volatile private var isReady = false
    @Volatile private var bestServerHost: String? = null
    @Volatile private var errorMessage: String? = null

    private val xrayBinary: File by lazy {
        File(context.applicationInfo.nativeLibraryDir, XRAY_BINARY_NAME)
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun start() {
        Log.d(TAG, "Start command received.")
        if (isRunning) {
            Log.w(TAG, "Manager is already running or starting. Command ignored.")
            return
        }

        connectionJob?.cancel()

        connectionJob = coroutineScope.launch {
            try {
                isRunning = true
                isReady = false
                errorMessage = null
                bestServerHost = null
                Log.d(TAG, "State -> Starting. Checking for Xray binary...")

                if (!xrayBinary.exists() || !xrayBinary.canExecute()) {
                    throw Exception("Xray binary not found or not executable at: ${xrayBinary.absolutePath}")
                }
                Log.d(TAG, "Binary found and executable: ${xrayBinary.absolutePath}")

                val customConfigs = getCustomConfigs()
                if (customConfigs.isNotEmpty()) {
                    Log.i(TAG, "Found ${customConfigs.size} custom configs. Using strict priority.")

                    for ((index, config) in customConfigs.withIndex()) {
                        val alias = config.optString("ps", "Custom Config #${index + 1}")
                        Log.i(TAG, "Attempting connection with Custom Config: $alias")

                        if (attemptConnection(config)) {
                            Log.i(TAG, "Successfully connected via Custom Config: $alias")
                            return@launch // Stop here, we are good
                        } else {
                            Log.w(TAG, "Custom config $alias failed. Trying next...")
                        }
                    }
                    Log.w(TAG, "All custom configs failed. Falling back to server configs.")
                }

                // 2. Fallback to Server Configs (if custom failed or empty)
                Log.d(TAG, "State -> Fetching Server Configs...")

                val githubConfigs = try {
                    fetchAndDecodeConfigs()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch GitHub configs", e)
                    emptyList()
                }

                if (githubConfigs.isEmpty()) {
                    throw Exception("No server configs available and custom configs failed/empty.")
                }

                // Auto-selection using TCP ping for server configs
                val sortedServers = pingAndSortAll(githubConfigs)

                if (sortedServers.isEmpty()) {
                    throw Exception("No responsive server VLESS configs found after pinging.")
                }

                Log.i(TAG, "Found ${sortedServers.size} responsive server configs. Trying them in order...")

                for ((index, serverConfig) in sortedServers.withIndex()) {
                    val host = serverConfig.extractHost()
                    // FIXED: Index starting from 1
                    val psName = serverConfig.optString("ps", "Server #${index + 1}")
                    Log.i(TAG, "Attempting connection with Server Config: $psName ($host)")

                    if (attemptConnection(serverConfig)) {
                        Log.i(TAG, "Successfully connected via Server Config: $psName")
                        return@launch
                    }
                }

                throw Exception("Failed to establish a working proxy connection with any server.")

            } catch (e: CancellationException) {
                Log.d(TAG, "Start sequence cancelled.")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in VlessManager start sequence", e)
                errorMessage = e.message ?: "Unknown error"
                stop() // Ensure full cleanup on final failure
            }
        }
    }

    /**
     * Tries to start Xray with the provided config and verifies the connection.
     * Returns true if successful, false otherwise.
     */
    private suspend fun attemptConnection(config: JSONObject): Boolean {
        return try {
            // Create a mutable copy for modification
            val configToUse = JSONObject(config.toString())
            val host = try { config.extractHost() } catch(e: Exception) { "unknown" }

            // Remove routing/dns blocks to avoid geoip/geosite errors
            configToUse.remove("routing")
            configToUse.remove("dns")

            // Patch TLS SNI if missing
            val outbound = configToUse.getJSONArray("outbounds").getJSONObject(0)
            outbound.optJSONObject("streamSettings")?.let { streamSettings ->
                if (streamSettings.optString("security") == "tls") {
                    val tlsSettings = streamSettings.optJSONObject("tlsSettings")
                    if (tlsSettings != null && !tlsSettings.has("serverName")) {
                        val sni = streamSettings.optJSONObject("wsSettings")?.optString("host")
                            ?: configToUse.optString("sni", "")
                        if (sni.isNotEmpty()) {
                            tlsSettings.put("serverName", sni)
                            Log.d(TAG, "Patched missing SNI with: $sni")
                        }
                    }
                }
            }

            val configFile = File(context.filesDir, "config.json")
            configFile.writeText(configToUse.toString(4))

            startXray()
            waitForProxyReady()

            // Real URL Test for the ACTIVE connection
            if (testProxyConnection()) {
                delay(100)
                isReady = true
                bestServerHost = host
                true
            } else {
                stopInternal() // Clean up the failed Xray process
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during connection attempt", e)
            stopInternal()
            false
        }
    }

    fun stop() {
        Log.d(TAG, "Stop command received.")
        connectionJob?.cancel() // Ensure the logic loop stops
        stopInternal()
        isRunning = false
        isReady = false
        bestServerHost = null
        Log.d(TAG, "State -> Stopped.")
    }

    /**
     * Internal stop function to clean up Xray process without changing the public state.
     */
    private fun stopInternal() {
        Log.d(TAG, "Internal cleanup initiated.")
        // Cancel readers first
        outReaderJob?.cancel()
        errReaderJob?.cancel()
        // A short blocking wait to ensure logs are flushed if possible.
        runBlocking {
            try { outReaderJob?.join() } catch (_: Exception) {}
            try { errReaderJob?.join() } catch (_: Exception) {}
        }
        outReaderJob = null
        errReaderJob = null

        // Stop process
        xrayProcess?.let { p ->
            try {
                p.destroy()
                if (!p.waitFor(500, TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly()
                    Log.w(TAG, "Xray process was forcibly destroyed.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping Xray: ${e.message}")
            }
        }
        xrayProcess = null
    }

    fun destroy() {
        Log.d(TAG, "Destroying VlessManager instance.")
        stop()
        coroutineScope.cancel()
    }

    // --- Public State Checkers ---
    fun isRunning(): Boolean = isRunning
    fun isReady(): Boolean = isReady
    fun getBestServerHost(): String? = bestServerHost
    fun getErrorMessage(): String? = errorMessage

    // --- Config Management & Utils ---

    suspend fun getGithubConfigs(): List<JSONObject> {
        return try {
            fetchAndDecodeConfigs()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting GitHub configs", e)
            emptyList()
        }
    }

    /**
     * Pings a specific server configuration.
     * Returns latency in milliseconds, or -1 if unreachable/error.
     */
    suspend fun pingServer(config: JSONObject): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val host = config.extractHost()
            val port = config.extractPort()
            measureTimeMillis {
                Socket().use { it.connect(InetSocketAddress(host, port), PING_TIMEOUT_MS) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ping failed for specific server", e)
            -1L
        }
    }

    fun addCustomConfig(jsonString: String) {
        try {
            // Validate JSON
            JSONObject(jsonString)
            val currentList = getCustomConfigs().map { it.toString() }.toMutableList()
            if (!currentList.contains(jsonString)) {
                currentList.add(jsonString)
                saveCustomConfigsList(currentList)

                if (isRunning) {
                    Log.i(TAG, "Custom config added while running. Restarting to apply...")
                    stop()
                    start()
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Invalid JSON provided for custom config", e)
            throw IllegalArgumentException("Invalid JSON format")
        }
    }

    fun removeCustomConfig(index: Int) {
        val currentList = getCustomConfigs().map { it.toString() }.toMutableList()
        if (index in 0 until currentList.size) {
            currentList.removeAt(index)
            saveCustomConfigsList(currentList)

            if (isRunning) {
                Log.i(TAG, "Custom config removed while running. Restarting to re-evaluate...")
                stop()
                start()
            }
        }
    }

    fun getCustomConfigs(): List<JSONObject> {
        val jsonString = prefs.getString(KEY_CUSTOM_CONFIGS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<JSONObject>()
            for (i in 0 until jsonArray.length()) {
                list.add(JSONObject(jsonArray.getString(i)))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved custom configs", e)
            emptyList()
        }
    }

    private fun saveCustomConfigsList(list: List<String>) {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_CUSTOM_CONFIGS, jsonArray.toString()).apply()
    }

    /**
     * Tests the proxy connection by making a real HTTP request through the local SOCKS port.
     */
    private suspend fun testProxyConnection(): Boolean = withContext(Dispatchers.IO) {
        val socksProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", PROXY_PORT))
        val client = OkHttpClient.Builder()
            .proxy(socksProxy)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        // Use a reliable, fast endpoint for connection testing (Google generate_204)
        val request = Request.Builder().url(CONNECTION_TEST_URL).build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Proxy test SUCCESS! Code: ${response.code}")

                    // Clean up connection pool in a separate coroutine
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            client.connectionPool.evictAll()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to evict connection pool", e)
                        }
                    }

                    return@withContext true
                } else {
                    Log.w(TAG, "Proxy test FAILED with code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Proxy test FAILED with exception", e)
        } finally {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    client.connectionPool.evictAll()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to evict connection pool in finally", e)
                }
            }
        }

        return@withContext false
    }


    private suspend fun fetchAndDecodeConfigs(): List<JSONObject> = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val response = client.newCall(Request.Builder().url(GITHUB_CONFIG_URL).build()).execute()
        if (!response.isSuccessful) throw Exception("Failed to download configs. Code: ${response.code}")

        val body = response.body?.string() ?: throw Exception("Empty response body")
        body.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                try {
                    val decoded = Base64.decode(trimmed, Base64.DEFAULT)
                    JSONObject(String(decoded, Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode config line: '$trimmed'", e)
                    null
                }
            } else null
        }
    }

    private suspend fun pingAndSortAll(configs: List<JSONObject>): List<JSONObject> = withContext(Dispatchers.IO) {
        if (configs.isEmpty()) return@withContext emptyList()

        val results = configs.map { config ->
            async {
                var ping = PING_TIMEOUT_MS + 1L
                try {
                    val host = config.extractHost()
                    val port = config.extractPort()
                    ping = measureTimeMillis {
                        Socket().use { it.connect(InetSocketAddress(host, port), PING_TIMEOUT_MS) }
                    }
                } catch (_: Exception) { }
                config to ping
            }
        }.awaitAll()

        return@withContext results
            .filter { it.second <= PING_TIMEOUT_MS }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun startXray() {
        val configFile = File(context.filesDir, "config.json")
        if (!configFile.exists()) throw Exception("Xray config file not found")

        val command = listOf(xrayBinary.absolutePath, "run", "-c", configFile.absolutePath)
        xrayProcess = ProcessBuilder(command).start()

        outReaderJob = coroutineScope.launch {
            val reader = xrayProcess?.inputStream?.bufferedReader() ?: return@launch
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    Log.i(TAG, "[Xray-out] $line")
                }
            } catch (e: InterruptedIOException) { }
            catch (e: IOException) {
                if (isActive) Log.w(TAG, "Xray stdout error: ${e.message}")
            } finally {
                try { reader.close() } catch (_: Exception) {}
            }
        }

        errReaderJob = coroutineScope.launch {
            val reader = xrayProcess?.errorStream?.bufferedReader() ?: return@launch
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    Log.e(TAG, "[Xray-err] $line")
                }
            } catch (e: InterruptedIOException) { }
            catch (e: IOException) {
                if (isActive) Log.w(TAG, "Xray stderr error: ${e.message}")
            } finally {
                try { reader.close() } catch (_: Exception) {}
            }
        }

        Log.d(TAG, "Xray process started with command: ${command.joinToString(" ")}")
    }


    private suspend fun waitForProxyReady(
        timeoutMs: Long = 10_000L,
        intervalMs: Long = 200L
    ) = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        while (isActive && System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", PROXY_PORT), 200)
                }
                return@withContext
            } catch (_: Exception) {
                if (isActive) delay(intervalMs) else break
            }
        }
        throw IllegalStateException("Proxy on port $PROXY_PORT did not become available within ${timeoutMs}ms.")
    }


    private fun JSONObject.extractHost(): String = this.getJSONArray("outbounds").getJSONObject(0)
        .getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).getString("address")

    private fun JSONObject.extractPort(): Int = this.getJSONArray("outbounds").getJSONObject(0)
        .getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).getInt("port")


    companion object {
        private const val TAG = "VLESS_MANAGER"
        private const val XRAY_BINARY_NAME = "libxray.so"
        private const val GITHUB_CONFIG_URL = "https://raw.githubusercontent.com/SMH01-MOD-NEXT/mod-next-proxy/refs/heads/main/vless.txt"
        private const val CONNECTION_TEST_URL = "https://www.google.com/generate_204"
        private const val PING_TIMEOUT_MS = 2000
        const val PROXY_PORT = 10808
        private const val PREFS_NAME = "vless_custom_configs"
        private const val KEY_CUSTOM_CONFIGS = "configs_list"

        @Volatile
        private var INSTANCE: VlessManager? = null

        fun getInstance(context: Context): VlessManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VlessManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}