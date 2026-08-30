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

package ru.protonmod.next.data.network.dns

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.utils.RegionUtils
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Name resolution that assumes the local network is hostile.
 *
 * The order is deliberate and is the whole point of this class:
 *
 *  1. **DoH, all providers at once.** Not one after another — under blocking,
 *     a dead provider costs a full timeout, and five sequential timeouts is a
 *     half-minute stall. Racing them means the answer arrives as fast as the
 *     fastest reachable resolver, and a blocked Cloudflare costs nothing
 *     because Google or Quad9 has already replied.
 *  2. **DoT on 853.** A different port with a different filtering rule. When
 *     443 to a known resolver is reset, this frequently still completes.
 *  3. **System DNS, and only if [HijackGuard] says it is honest.** This is the
 *     inversion of the old behaviour, which asked the system first and treated
 *     a poisoned-but-successful answer as the truth.
 *
 * Answers are cached in memory only. Resolution results describe the network
 * currently attached and say something about where the user is, so they are
 * never written to disk.
 */
@Singleton
class SecureDnsResolver @Inject constructor(
    private val hijackGuard: HijackGuard,
    private val dotClient: DotClient,
    private val settingsManagerProvider: Provider<SettingsManager>,
) : Dns {

    private companion object {
        const val TAG = "SecureDns"

        /** Per-provider budget. Short: a slow resolver loses the race, it does not stall it. */
        const val DOH_TIMEOUT_SECONDS = 5L

        /** Ceiling for the whole DoH stage, however many providers are racing. */
        const val DOH_RACE_TIMEOUT_MS = 7_000L

        const val DOT_TIMEOUT_MS = 5_000

        /**
         * Positive-answer lifetime. OkHttp's DoH client does not surface record
         * TTLs, so a fixed window is used: long enough to keep a connection
         * burst off the resolvers, short enough that a server rotation is
         * picked up without restarting the app.
         */
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }

    private data class CacheEntry(val addresses: List<InetAddress>, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val dohClients = ConcurrentHashMap<String, DnsOverHttps>()

    /**
     * Daemon threads: DNS races must never keep the process alive, and a
     * cached pool shrinks back to nothing between bursts.
     */
    private val raceExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "secure-dns").apply { isDaemon = true }
    }

    /**
     * Carries no [Dns] of its own beyond a literal-only guard, so a bug in
     * provider configuration can never quietly fall back to the system
     * resolver while trying to reach a DoH endpoint.
     */
    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(LITERAL_ONLY_DNS)
            .connectTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(DOH_TIMEOUT_SECONDS + 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // An address that is already literal needs no resolver at all.
        literalOrNull(hostname)?.let { return listOf(it) }

        cached(hostname)?.let { return it }

        val providers = orderedProviders()

        raceDoh(hostname, providers)?.let {
            ProtonLogger.d(TAG, "DoH resolved $hostname")
            return remember(hostname, it)
        }

        if (isDotFallbackEnabled()) {
            for (provider in providers) {
                val answers = dotClient.resolve(provider, hostname, DOT_TIMEOUT_MS)
                if (answers.isNotEmpty() && !hijackGuard.isPoisoned(answers)) {
                    ProtonLogger.i(TAG, "DoT via ${provider.displayName} resolved $hostname")
                    return remember(hostname, answers)
                }
            }
        }

        // Last resort, and gated. On a network that rewrites NXDOMAIN this
        // branch is skipped entirely rather than accepting a block-page address.
        if (hijackGuard.isSystemResolverTrustworthy()) {
            val system = try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                emptyList()
            }
            if (system.isNotEmpty() && !hijackGuard.isPoisoned(system)) {
                ProtonLogger.d(TAG, "System resolver answered for $hostname and passed the poison check")
                return remember(hostname, system)
            }
        } else {
            ProtonLogger.w(
                TAG,
                "Refusing the system resolver for $hostname: it rewrites answers (NSDI-style redirect)"
            )
        }

        throw UnknownHostException("No trusted resolver could answer for $hostname")
    }

    /**
     * Runs every provider concurrently and returns the first clean answer.
     *
     * Losing entries are cancelled as soon as a winner appears, so a blocked
     * provider's timeout is never waited on.
     */
    private fun raceDoh(
        hostname: String,
        providers: List<DnsProviders.Provider>,
    ): List<InetAddress>? {
        if (providers.isEmpty()) return null

        val completion = ExecutorCompletionService<List<InetAddress>>(raceExecutor)
        val pending = providers.map { provider ->
            completion.submit { resolveOverDoh(provider, hostname) }
        }

        val deadline = System.currentTimeMillis() + DOH_RACE_TIMEOUT_MS
        try {
            repeat(providers.size) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return null

                val finished = completion.poll(remaining, TimeUnit.MILLISECONDS) ?: return null
                val answers = try {
                    finished.get()
                } catch (e: Exception) {
                    emptyList()
                }

                if (answers.isNotEmpty() && !hijackGuard.isPoisoned(answers)) return answers
            }
            return null
        } finally {
            pending.forEach { it.cancel(true) }
        }
    }

    private fun resolveOverDoh(
        provider: DnsProviders.Provider,
        hostname: String,
    ): List<InetAddress> = try {
        dohClientFor(provider).lookup(hostname)
    } catch (e: Exception) {
        ProtonLogger.d(TAG, "DoH ${provider.displayName} failed for $hostname: ${e.message}")
        emptyList()
    }

    /**
     * Builds — once per provider — the DoH client.
     *
     * [DnsProviders.Provider.dohIpUrl] is preferred where the operator's
     * certificate allows it: an IP-literal endpoint has no name to look up and
     * presents no hostname in the TLS handshake for DPI to match on. Providers
     * without an IP SAN fall back to the named endpoint, still reached through
     * pinned bootstrap literals rather than the system resolver.
     */
    private fun dohClientFor(provider: DnsProviders.Provider): DnsOverHttps =
        dohClients.getOrPut(provider.id) {
            val endpoint = provider.dohIpUrl ?: provider.dohHostUrl
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(endpoint.toHttpUrl())
                .bootstrapDnsHosts(provider.addresses.mapNotNull { literalOrNull(it) })
                .includeIPv6(true)
                .build()
        }

    /** User's preferred provider first, then the rest of the trusted set. */
    private fun orderedProviders(): List<DnsProviders.Provider> {
        val preferredId = try {
            settingsManagerProvider.get().getDnsProviderIdSync()
        } catch (e: Exception) {
            ""
        }
        val preferred = DnsProviders.byId(preferredId)
        if (preferred == null) return DnsProviders.ALL
        return listOf(preferred) + DnsProviders.ALL.filter { it.id != preferred.id }
    }

    /**
     * Whether the DoT stage may run. Forced on inside Russia.
     *
     * The preference exists for networks where 853 is merely slow and the user
     * would rather fail fast. Under RKN that is not a trade-off worth offering:
     * DoH on 443 is actively filtered, so 853 is often the last encrypted path,
     * and switching it off hands resolution back to a system resolver that
     * answers with NSDI block pages. The stored value is therefore ignored
     * rather than merely defaulted to true, so neither a stale preference nor a
     * restored backup can disable it.
     */
    private fun isDotFallbackEnabled(): Boolean {
        if (RegionUtils.isRussianRegion()) return true
        return try {
            settingsManagerProvider.get().isDnsOverTlsFallbackEnabledSync()
        } catch (e: Exception) {
            true
        }
    }

    private fun cached(hostname: String): List<InetAddress>? {
        val entry = cache[hostname] ?: return null
        if (System.currentTimeMillis() >= entry.expiresAtMs) {
            cache.remove(hostname)
            return null
        }
        return entry.addresses
    }

    /**
     * Stores the answer IPv4-first.
     *
     * OkHttp attempts addresses in the order given, and on an IPv4-only mobile
     * network a leading IPv6 address costs a full connect failure first.
     */
    private fun remember(hostname: String, addresses: List<InetAddress>): List<InetAddress> {
        val sorted = addresses.sortedWith(compareBy { if (it is Inet4Address) 0 else 1 })
        cache[hostname] = CacheEntry(sorted, System.currentTimeMillis() + CACHE_TTL_MS)
        return sorted
    }

    /**
     * Clears cached answers and the hijack verdict.
     *
     * Must be called when connectivity changes: answers and the verdict both
     * describe one specific network, and carrying them onto the next one is
     * how a client ends up talking to the wrong address after a Wi-Fi to
     * mobile handover.
     */
    fun invalidate() {
        cache.clear()
        hijackGuard.invalidate()
    }
}

/**
 * Accepts an address only when it is already numeric.
 *
 * [InetAddress.getByName] performs no lookup for a literal, so this stays
 * offline; anything else is refused rather than silently handed to the system
 * resolver.
 */
private val LITERAL_ONLY_DNS = Dns { hostname ->
    literalOrNull(hostname)?.let { listOf(it) }
        ?: throw UnknownHostException("Refusing to resolve $hostname outside the encrypted resolvers")
}

private val IPV4_PATTERN = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

private fun literalOrNull(host: String): InetAddress? {
    val candidate = host.trim().removeSurrounding("[", "]")
    if (candidate.isEmpty()) return null
    val numeric = candidate.contains(':') || IPV4_PATTERN.matches(candidate)
    if (!numeric) return null
    return try {
        InetAddress.getByName(candidate)
    } catch (e: Exception) {
        null
    }
}
