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

import ru.protonmod.next.utils.ProtonLogger
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Decides whether the network's own resolver can be believed.
 *
 * The problem this exists for: an NSDI redirect is not a failure. The query
 * succeeds, the response is well-formed, and it carries an A record — just one
 * pointing at a block page instead of the real host. Code that falls back to
 * encrypted DNS "if system DNS throws" therefore never falls back at all, which
 * is exactly the hole this class closes.
 *
 * Detection uses a canary rather than a list of block-page addresses, because
 * those rotate and a stale list fails silently. RFC 2606 reserves `.invalid`
 * and guarantees it never resolves, so a random name under it is a question
 * with only one honest answer: NXDOMAIN. A resolver that returns an address
 * instead has told us two things at once — that it rewrites answers, and what
 * address it rewrites them to. That address is recorded as a [sentinel] and
 * every later answer can be checked against it.
 */
@Singleton
class HijackGuard @Inject constructor() {

    enum class Verdict {
        /** The resolver answered NXDOMAIN honestly. */
        CLEAN,

        /** The resolver invented an address. Nothing it says may be trusted. */
        HIJACKED,

        /** No conclusive probe yet, e.g. the network is down. Treat as unsafe. */
        UNKNOWN,
    }

    private companion object {
        const val TAG = "HijackGuard"

        /** Re-probe this often; long enough to be cheap, short enough to notice a network change. */
        const val VERDICT_TTL_MS = 5 * 60 * 1000L

        /**
         * Two probes, not one. A single lookup can fail for ordinary reasons
         * (packet loss, a resolver that is briefly down) and reporting HIJACKED
         * for that would push every user onto the slow path for no reason.
         */
        const val PROBE_COUNT = 2
    }

    /**
     * Addresses observed being handed back for names that cannot exist, i.e.
     * the local block page. Never persisted: it is only meaningful for the
     * network currently attached.
     */
    private val sentinels = CopyOnWriteArraySet<String>()

    @Volatile
    private var cachedVerdict: Verdict = Verdict.UNKNOWN

    @Volatile
    private var verdictAtMs: Long = 0L

    /** Probes are serialised so a burst of parallel lookups causes one check. */
    private val probeLock = Any()

    /**
     * Current verdict, re-probing when the cached one has aged out.
     *
     * Blocking, and intended to be: callers are OkHttp's DNS callback and the
     * resolver, both already off the main thread.
     */
    fun verdict(): Verdict {
        val now = System.currentTimeMillis()
        val cached = cachedVerdict
        if (cached != Verdict.UNKNOWN && now - verdictAtMs < VERDICT_TTL_MS) return cached

        synchronized(probeLock) {
            // Another thread may have completed a probe while this one waited.
            val fresh = cachedVerdict
            if (fresh != Verdict.UNKNOWN && System.currentTimeMillis() - verdictAtMs < VERDICT_TTL_MS) {
                return fresh
            }
            val verdict = probe()
            cachedVerdict = verdict
            verdictAtMs = System.currentTimeMillis()
            return verdict
        }
    }

    /** Convenience for the common branch: may the system resolver be used at all? */
    fun isSystemResolverTrustworthy(): Boolean = verdict() == Verdict.CLEAN

    /**
     * True when [addresses] look like a rewritten answer rather than a real one.
     *
     * Two independent signals. The first is the sentinel set: an address the
     * resolver already proved it invents. The second is structural — a public
     * hostname resolving to a loopback, wildcard or link-local address is not a
     * real answer, and NXDOMAIN rewriting commonly lands there.
     */
    fun isPoisoned(addresses: List<InetAddress>): Boolean {
        if (addresses.isEmpty()) return false
        return addresses.all { address ->
            val literal = address.hostAddress ?: return@all false
            literal in sentinels ||
                address.isLoopbackAddress ||
                address.isAnyLocalAddress ||
                address.isLinkLocalAddress
        }
    }

    /** Block-page addresses discovered so far. Exposed for diagnostics and tests. */
    fun sentinels(): Set<String> = sentinels.toSet()

    /**
     * Drops the cached verdict. Call when connectivity changes: a verdict from
     * a home Wi-Fi network says nothing about the mobile network replacing it.
     */
    fun invalidate() {
        cachedVerdict = Verdict.UNKNOWN
        verdictAtMs = 0L
        sentinels.clear()
    }

    /**
     * Asks the system resolver for names that cannot exist.
     *
     * NXDOMAIN surfaces as [java.net.UnknownHostException]; anything else is a
     * fabricated answer. A probe that fails to complete leaves the verdict
     * UNKNOWN rather than CLEAN, so an unreachable resolver never gets trusted
     * by default.
     */
    private fun probe(): Verdict {
        var sawHonestNxdomain = false

        repeat(PROBE_COUNT) {
            val canary = randomCanaryName()
            try {
                val answers = InetAddress.getAllByName(canary)
                if (answers.isEmpty()) {
                    sawHonestNxdomain = true
                    return@repeat
                }

                // An address for a `.invalid` name is proof of rewriting, and
                // the address itself is the block page.
                val discovered = answers.mapNotNull { it.hostAddress }
                sentinels.addAll(discovered)
                ProtonLogger.w(
                    TAG,
                    "System resolver rewrites NXDOMAIN: $canary resolved to ${discovered.joinToString(", ")}"
                )
                return Verdict.HIJACKED
            } catch (e: java.net.UnknownHostException) {
                // The correct answer.
                sawHonestNxdomain = true
            } catch (e: Exception) {
                ProtonLogger.d(TAG, "Canary probe could not complete: ${e.message}")
            }
        }

        return if (sawHonestNxdomain) {
            ProtonLogger.d(TAG, "System resolver answered NXDOMAIN correctly")
            Verdict.CLEAN
        } else {
            Verdict.UNKNOWN
        }
    }

    /**
     * A random label under `.invalid`. Random so a resolver cannot special-case
     * it, and `.invalid` so the query never leaves a recursive resolver or
     * reaches a real authority.
     */
    private fun randomCanaryName(): String {
        val label = buildString(16) {
            repeat(16) { append(('a' + Random.nextInt(26))) }
        }
        return "$label.invalid"
    }
}
