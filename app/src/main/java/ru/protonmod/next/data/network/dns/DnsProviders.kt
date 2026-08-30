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

/**
 * Curated registry of encrypted DNS resolvers.
 *
 * Two rules govern everything in this file.
 *
 * **No Russian resolvers, ever.** Plaintext DNS inside RU is redirected to NSDI
 * answers, so a resolver operating under RU jurisdiction is not a fallback, it
 * is the attack. [DENIED_RESOLVERS] blocks the well-known ones from being
 * reintroduced through settings, restore-from-backup or a future edit here.
 *
 * **Address resolvers by IP, never by name.** A DoH endpoint written as
 * `https://cloudflare-dns.com/dns-query` has to be resolved by *something*
 * first, and on a hijacked network that something is the hijacker. Every entry
 * therefore carries literal [addresses], and the providers whose certificates
 * include IP SANs also carry a [dohIpUrl] that skips naming altogether — which
 * additionally removes the SNI hostname that DPI keys on.
 */
object DnsProviders {

    /**
     * A resolver we are willing to trust, with every address needed to reach it
     * without asking the local network for help.
     */
    data class Provider(
        /** Stable key persisted in settings. Never localise or reuse. */
        val id: String,
        /** Operator name, shown as-is (a brand, so it is not translated). */
        val displayName: String,
        /** ISO 3166-1 alpha-2 jurisdiction the operator answers to. */
        val jurisdiction: String,
        /**
         * DoH endpoint addressed by IP literal, or null when the operator's
         * certificate has no IP SAN and this would fail TLS validation.
         * Preferred whenever present: no name to resolve, no SNI to filter.
         */
        val dohIpUrl: String?,
        /** Conventional name-based DoH endpoint, used with [addresses]. */
        val dohHostUrl: String,
        /** DoT name (SNI and certificate identity) on port 853. */
        val dotHostname: String,
        /** Literal bootstrap addresses, IPv4 first. */
        val addresses: List<String>,
    )

    const val CLOUDFLARE = "cloudflare"
    const val GOOGLE = "google"
    const val QUAD9 = "quad9"
    const val MULLVAD = "mullvad"
    const val DNS0 = "dns0"

    /**
     * Trusted resolvers in default preference order.
     *
     * Cloudflare leads because the user requires it kept working by any means
     * available, and it is the only entry reachable over four distinct literals
     * plus an IP-SAN certificate. Google follows for the same reason. Quad9,
     * Mullvad and dns0.eu are the non-US second opinions: all three are
     * established, audited, no-logging operators rather than no-name endpoints,
     * and none is subject to RU jurisdiction.
     */
    val ALL: List<Provider> = listOf(
        Provider(
            id = CLOUDFLARE,
            displayName = "Cloudflare",
            jurisdiction = "US",
            // 1.1.1.1 and 1.0.0.1 are IP SANs on the cloudflare-dns.com leaf,
            // so TLS validates against the literal with no name involved.
            dohIpUrl = "https://1.1.1.1/dns-query",
            dohHostUrl = "https://cloudflare-dns.com/dns-query",
            dotHostname = "one.one.one.one",
            addresses = listOf(
                "1.1.1.1",
                "1.0.0.1",
                "2606:4700:4700::1111",
                "2606:4700:4700::1001",
            ),
        ),
        Provider(
            id = GOOGLE,
            displayName = "Google Public DNS",
            jurisdiction = "US",
            // dns.google carries 8.8.8.8 and 8.8.4.4 as IP SANs.
            dohIpUrl = "https://8.8.8.8/dns-query",
            dohHostUrl = "https://dns.google/dns-query",
            dotHostname = "dns.google",
            addresses = listOf(
                "8.8.8.8",
                "8.8.4.4",
                "2001:4860:4860::8888",
                "2001:4860:4860::8844",
            ),
        ),
        Provider(
            id = QUAD9,
            displayName = "Quad9",
            jurisdiction = "CH",
            // dns.quad9.net lists 9.9.9.9 and 149.112.112.112 as IP SANs.
            dohIpUrl = "https://9.9.9.9/dns-query",
            dohHostUrl = "https://dns.quad9.net/dns-query",
            dotHostname = "dns.quad9.net",
            addresses = listOf(
                "9.9.9.9",
                "149.112.112.112",
                "2620:fe::fe",
                "2620:fe::9",
            ),
        ),
        Provider(
            id = MULLVAD,
            displayName = "Mullvad DNS",
            jurisdiction = "SE",
            // No documented IP SAN: addressing the literal would fail TLS, so
            // this one is reached by name over the bootstrap addresses below.
            dohIpUrl = null,
            dohHostUrl = "https://dns.mullvad.net/dns-query",
            dotHostname = "dns.mullvad.net",
            addresses = listOf(
                "194.242.2.2",
                "2a07:e340::2",
            ),
        ),
        Provider(
            id = DNS0,
            displayName = "dns0.eu",
            jurisdiction = "EU",
            // Same as Mullvad: name-addressed, literals used only to bootstrap.
            dohIpUrl = null,
            dohHostUrl = "https://dns0.eu/",
            dotHostname = "dns0.eu",
            addresses = listOf(
                "193.110.81.0",
                "185.253.5.0",
                "2a0f:fc80::",
                "2a0f:fc81::",
            ),
        ),
    )

    /** Order used when the user has expressed no preference. */
    val DEFAULT_ORDER: List<String> = ALL.map { it.id }

    fun byId(id: String): Provider? = ALL.firstOrNull { it.id == id }

    /**
     * Resolvers that must never be used, whatever the source.
     *
     * Everything here either operates under RU jurisdiction or is the hijack
     * itself. Kept as literals because these are the addresses a user is
     * realistically talked into pasting into the custom-DNS field.
     *
     * This list is a safety net, not the defence. Block-page addresses rotate,
     * so correctness rests on [HijackGuard]'s canary plus the allowlist above;
     * this only stops the known-bad from being configured on purpose.
     */
    val DENIED_RESOLVERS: Set<String> = setOf(
        // Yandex.DNS — RU jurisdiction, participates in national filtering.
        "77.88.8.8", "77.88.8.1", "77.88.8.88", "77.88.8.2", "77.88.8.7", "77.88.8.3",
        // SkyDNS — RU filtering service.
        "193.58.251.251",
        // Comss.one DNS — RU operated.
        "83.220.169.155", "212.109.195.93",
        // Major RU ISP resolvers handed out by DHCP, all NSDI-connected.
        "213.158.0.6", "212.48.193.36", "195.34.32.116", "195.34.32.117", // Rostelecom
        "212.188.4.10", "213.87.0.1", // MTS
        "217.118.66.243", "213.234.192.8", // Beeline
        "83.149.32.15", // MegaFon
    )

    /**
     * IPv6 prefixes and IPv4 /24s belonging to denied operators, catching the
     * sibling addresses that are not worth enumerating one by one.
     */
    private val DENIED_PREFIXES: List<String> = listOf(
        "77.88.8.",        // Yandex.DNS
        "2a02:6b8::feed",  // Yandex.DNS over IPv6
    )

    /**
     * True when [address] is a resolver we refuse to send queries to.
     *
     * Applied to user input as well as to anything restored from a backup, so a
     * previously saved Russian resolver cannot come back after an upgrade.
     */
    fun isDenied(address: String): Boolean {
        val normalised = address.trim().lowercase().removeSurrounding("[", "]")
        if (normalised.isEmpty()) return false
        if (normalised in DENIED_RESOLVERS) return true
        return DENIED_PREFIXES.any { normalised.startsWith(it) }
    }
}
