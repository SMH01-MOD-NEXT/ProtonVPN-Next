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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.protonmod.next.data.network.dns.DnsProviders
import ru.protonmod.next.data.network.dns.SecureDnsResolver
import ru.protonmod.next.utils.Base32
import ru.protonmod.next.utils.ProtonLogger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DohResponse(
    @SerialName("Status") val status: Int,
    @SerialName("Answer") val answer: List<DohAnswer>? = null
)

@Serializable
data class DohAnswer(
    @SerialName("name") val name: String,
    @SerialName("type") val type: Int,
    @SerialName("data") val data: String
)

/**
 * Proton's alternative-routing discovery: a TXT lookup under `protonpro.xyz`
 * that returns the addresses to use when the API's own domain is unreachable.
 *
 * This used to query `https://dns.google/resolve` and
 * `https://cloudflare-dns.com/dns-query` by name. Both hostnames had to be
 * resolved before the request could be sent, and the only resolver available
 * to do that was the system one — which, on a network redirecting DNS to NSDI,
 * is the thing being worked around. Discovery therefore failed exactly when it
 * was needed, and did so silently, because a redirected answer is a successful
 * lookup.
 *
 * Two changes fix that. Endpoints are addressed by IP literal, so there is no
 * name to resolve and no SNI hostname for DPI to match on; and the client is
 * given [SecureDnsResolver] rather than the default, so that if a named
 * endpoint is ever added here it still cannot reach the system resolver.
 */
@Singleton
class DohClient @Inject constructor(
    private val json: Json,
    secureDnsResolver: SecureDnsResolver,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .dns(secureDnsResolver)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * JSON-API endpoints, primary and secondary address for each operator.
     *
     * Only Cloudflare and Google appear here, and deliberately so: the JSON
     * DoH API is not part of RFC 8484 and the other trusted providers in
     * [DnsProviders] either do not serve it or do not serve it on a literal
     * with a matching certificate. Those providers still back ordinary
     * resolution through [SecureDnsResolver]; this list covers only the TXT
     * discovery query, where the JSON shape below is required.
     *
     * Cloudflare uses `/dns-query`, Google uses `/resolve`. Both certificates
     * carry the IP SANs used here, so TLS validates against the literal.
     */
    private val providers = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/resolve",
        "https://1.0.0.1/dns-query",
        "https://8.8.4.4/resolve",
    )

    suspend fun getAlternativeHosts(sessionId: String?, originalHost: String): List<String> {
        val base32Host = Base32.encode(originalHost.toByteArray())
        val sessionPrefix = if (sessionId != null) "$sessionId." else ""
        val queryDomain = "${sessionPrefix}d$base32Host.protonpro.xyz"

        ProtonLogger.d("DohClient", "Querying alternative hosts for: $queryDomain")

        for (provider in providers) {
            try {
                val url = provider.toHttpUrl().newBuilder()
                    .addQueryParameter("name", queryDomain)
                    .addQueryParameter("type", "TXT")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body
                        val body = responseBody.string()
                        val dohResponse = json.decodeFromString<DohResponse>(body)
                        if (dohResponse.status == 0 && dohResponse.answer != null) {
                            return dohResponse.answer
                                .filter { it.type == 16 } // TXT record
                                .map { it.data.trim('"') }
                                .filter { it.isNotEmpty() }
                        }
                    }
                }
            } catch (e: Exception) {
                ProtonLogger.w("DohClient", "Failed to query DoH provider $provider: ${e.message}")
            }
        }

        return emptyList()
    }
}
