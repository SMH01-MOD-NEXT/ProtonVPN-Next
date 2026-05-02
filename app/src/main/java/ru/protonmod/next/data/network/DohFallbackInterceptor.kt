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
 * along with this program.  I not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.data.network

import okhttp3.Interceptor
import okhttp3.Response
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.ProtonLogger
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class DohFallbackInterceptor @Inject constructor(
    private val dohClient: DohClient,
    private val sessionDao: SessionDao,
    private val fallbackStore: DohFallbackStore,
    private val settingsManager: SettingsManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalUrl = request.url
        val originalHost = originalUrl.host

        // Only apply to Proton API and related subdomains
        val isProtonApi = (originalHost == "vpn-api.proton.me" || 
                          originalHost == "api.protonmail.ch" ||
                          originalHost == "api.protonvpn.ch" ||
                          originalHost == "api.protonmail.com" ||
                          originalHost == "mail.proton.me" ||
                          originalHost == "shimmering-stroopwafel-51675e.netlify.app" ||
                          originalHost == "api.protonnext.qzz.io")

        if (!isProtonApi) {
            return chain.proceed(request)
        }

        val useProxy = settingsManager.isApiBypassEnabledSync()
        val strategy = settingsManager.getApiBypassStrategySync()
        val isMirrorsStrategy = useProxy && strategy == SettingsManager.STRATEGY_PROTON_MIRRORS
        
        ProtonLogger.d("DohFallback", "Intercepting $originalHost. Enabled: $useProxy, Strategy: $strategy, isMirrors: $isMirrorsStrategy")

        var currentRequest = request
        val fallbackIps = fallbackStore.getFallbackIps(originalHost)

        // If Mirrors strategy is active, try to use IP directly to bypass SNI
        if (isMirrorsStrategy) {
            val ips = if (fallbackIps.isNullOrEmpty()) {
                ProtonLogger.i("DohFallback", "Mirrors strategy active for $originalHost. Triggering proactive discovery.")
                try {
                    discoverAndStore(originalHost)
                } catch (e: Exception) {
                    ProtonLogger.w("DohFallback", "Proactive discovery failed: ${e.message}")
                    emptyList()
                }
            } else fallbackIps

            if (ips.isNotEmpty()) {
                // Use the first IP: the list is sorted IPv4-first so this prefers IPv4
                // on networks without IPv6 connectivity.
                val ip = ips.first().hostAddress
                if (ip != null) {
                    currentRequest = buildIpRequest(request, ip, originalHost)
                }
            }
        }

        try {
            return chain.proceed(currentRequest)
        } catch (e: IOException) {
            ProtonLogger.w("DohFallback", "Request failed for $originalHost: ${e.message}. Attempting discovery.")
            
            val altHosts = try {
                discoverAndStore(originalHost)
            } catch (discoveryEx: Exception) {
                throw e
            }

            if (altHosts.isEmpty()) throw e

            // Retry with the first discovered IP (list is IPv4-first, so prefer IPv4)
            val altIp = altHosts.first().hostAddress
            if (altIp == null) throw e

            try {
                val retryRequest = buildIpRequest(request, altIp, originalHost)
                ProtonLogger.d("DohFallback", "Retrying with alt IP: $altIp")
                val response = chain.proceed(retryRequest)
                if (response.isSuccessful) {
                    ProtonLogger.i("DohFallback", "Successfully connected to $originalHost via fallback IP $altIp")
                }
                return response
            } catch (retryException: IOException) {
                ProtonLogger.e("DohFallback", "Retry for $originalHost failed even with DoH fallback IP: ${retryException.message}")
                throw retryException
            }
        }
    }

    private fun buildIpRequest(request: okhttp3.Request, ip: String, originalHost: String): okhttp3.Request {
        val newUrl = request.url.newBuilder()
            .host(ip)
            .build()
        return request.newBuilder()
            .url(newUrl)
            .header("Host", originalHost)
            .build()
    }

    private fun discoverAndStore(host: String): List<java.net.InetAddress> {
        val altHosts = runBlocking {
            val session = sessionDao.getSession()
            dohClient.getAlternativeHosts(session?.sessionId, host)
        }

        if (altHosts.isEmpty()) {
            ProtonLogger.w("DohFallback", "No alternative hosts found via DoH for $host.")
            return emptyList()
        }

        val ipAddresses = altHosts.joinToString(", ")
        ProtonLogger.i("DohFallback", "Discovered ${altHosts.size} alternative IPs for $host: $ipAddresses")

        fallbackStore.setFallbackIps(host, altHosts)
        // getFallbackIps returns addresses already sorted IPv4-first (see DohFallbackStore).
        return fallbackStore.getFallbackIps(host) ?: emptyList()
    }
}
