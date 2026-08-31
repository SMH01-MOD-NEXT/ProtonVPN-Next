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

package ru.protonmod.next.data.network.ip

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where the app asks what its own address is.
 *
 * The sources that matter here are deployments this project already runs, each
 * answering `/__proxy/whoami` with the address it sees. They are asked before
 * anything else for a plain reason: the public what-is-my-IP services this used
 * to depend on are, from Russia, either unreachable or unwilling, so the
 * feature failed exactly where the app is needed most.
 *
 * One of those services was reachable only over cleartext HTTP. That is worse
 * than useless: the platform blocks it outright, and had it not, the request
 * asking "what is my address" would have travelled unencrypted. Nothing here
 * is allowed to be plain HTTP, including a bypass host handed to the app at
 * runtime.
 */
object IpEchoSources {

    /** The path every deployment of the proxy answers with the caller's address. */
    const val WHOAMI_PATH = "/__proxy/whoami"

    const val CLOUDFLARE_ORIGIN = "https://protonvpn-next-web.smh01.workers.dev"

    /**
     * The same Cloudflare deployment under the project's own name.
     *
     * Sharing one hostname with every free Worker is what makes the
     * `workers.dev` spelling easy to filter wholesale, so this is the copy to
     * ask first: it is the name the site itself is served under.
     */
    const val CLOUDFLARE_SITE_ORIGIN = "https://home.protonnext.qzz.io"
    const val DENO_ORIGIN = "https://protonvpn-next-web--main.smh01-mirrors.deno.net"
    const val VERCEL_ORIGIN = "https://proton-vpn-next-web.vercel.app"

    /**
     * Vercel routes to the proxy only through `/api`, so the path travels as a
     * query parameter. Asking for `WHOAMI_PATH` directly there returns the
     * static site's 404 page instead of an address.
     */
    const val VERCEL_WHOAMI = "$VERCEL_ORIGIN/api?__path=$WHOAMI_PATH"

    /**
     * A place to ask, and whether this project owns it.
     *
     * `isOwn` earns two privileges: only our own deployments are asked again
     * after a failure, and only they are trusted to name the country for an
     * address, because doing so means showing them that address.
     */
    data class Source(val id: String, val url: String, val isOwn: Boolean)

    private val cloudflare = Source("cloudflare", CLOUDFLARE_ORIGIN + WHOAMI_PATH, isOwn = true)
    private val cloudflareSite =
        Source("cloudflare-site", CLOUDFLARE_SITE_ORIGIN + WHOAMI_PATH, isOwn = true)
    private val deno = Source("deno", DENO_ORIGIN + WHOAMI_PATH, isOwn = true)
    private val vercel = Source("vercel", VERCEL_WHOAMI, isOwn = true)

    /**
     * Last resort, and deliberately short.
     *
     * Kept only so a user outside Russia whose network blocks our deployments
     * still sees something. Both speak HTTPS; the third service that used to be
     * in this list did not.
     */
    private val publicFallbacks = listOf(
        Source("api.myip.com", "https://api.myip.com", isOwn = false),
        Source("freeipapi", "https://freeipapi.com/api/json", isOwn = false),
    )

    /**
     * The sources to try, in order.
     *
     * Ours come first, always. Outside Russia the two that report a country in
     * the same answer lead, which saves a second request. Inside Russia
     * reachability decides instead: Deno answers there, Cloudflare is
     * throttled, and a temporary event bypass — which exists precisely because
     * the fixed hosts stopped being reachable — is tried before either of the
     * hosts it was meant to stand in for.
     */
    fun ordered(isRussianRegion: Boolean, eventBypassUrl: String = ""): List<Source> {
        val event = originOf(eventBypassUrl)?.let { Source("event", it + WHOAMI_PATH, isOwn = true) }

        val own = if (isRussianRegion) {
            listOfNotNull(deno, event, cloudflareSite, vercel, cloudflare)
        } else {
            listOfNotNull(cloudflareSite, cloudflare, vercel, deno, event)
        }

        return own + publicFallbacks
    }

    /**
     * Who to ask for a country when a deployment reports an address without
     * one, in the order to try.
     *
     * Only a host that is itself told the caller's country can answer this:
     * Cloudflare and Vercel are, the Deno deployment is not and returns the
     * field empty. Cloudflare appears twice, under the project's own name
     * first, because that name is the one that stays reachable. That is why this is a list rather than a single URL. The
     * earlier version asked Cloudflare alone — the very host ranked last inside
     * Russia for being unreachable — so an address resolved through Deno could
     * never be given a country there, which is exactly how it failed.
     *
     * Both entries are ours, so the address is never handed to a geolocation
     * service merely to be labelled.
     */
    fun countryProbeSources(isRussianRegion: Boolean): List<Source> =
        if (isRussianRegion) {
            listOf(cloudflareSite, vercel, cloudflare)
        } else {
            listOf(cloudflareSite, cloudflare, vercel)
        }

    /**
     * One spelling per address, mirroring `normaliseAddress` on the server.
     *
     * The server normalises too, but an installed app talks to whichever
     * version of a deployment happens to be live, and the IPv4-in-IPv6 form
     * `::ffff:1.2.3.4` is what the Deno mirror returned before that fix landed.
     * Normalising at both ends means the address a user reads never depends on
     * which mirror answered, or on when it was last deployed.
     */
    fun normaliseAddress(raw: String): String {
        var address = raw.trim()
        if (address.isEmpty()) return ""

        BRACKETED.matchEntire(address)?.let { address = it.groupValues[1] }
        WITH_PORT.matchEntire(address)?.let { address = it.groupValues[1] }
        MAPPED_IPV4.matchEntire(address)?.let { address = it.groupValues[1] }

        return address
    }

    /**
     * Scheme, host and any non-default port of a stored URL.
     *
     * @return null when the value is empty, unparseable or not HTTPS. The last
     *   case is the important one: this URL arrives from remote configuration,
     *   and refusing cleartext here means no future bypass entry can talk the
     *   app into sending an address-revealing request in the clear.
     */
    private fun originOf(url: String): String? {
        val parsed = url.trim().ifEmpty { return null }.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https") return null

        val port = if (parsed.port == 443) "" else ":${parsed.port}"
        return "${parsed.scheme}://${parsed.host}$port"
    }

    // `[2001:db8::1]:443` -> `2001:db8::1`
    private val BRACKETED = Regex("""^\[(.+)](?::\d+)?$""")

    // `1.2.3.4:51234` -> `1.2.3.4`. Anchored on the dotted quad so a bare IPv6,
    // which is nothing but colons, is never mistaken for a host:port pair.
    private val WITH_PORT = Regex("""^(\d{1,3}(?:\.\d{1,3}){3}):\d+$""")

    // `::ffff:1.2.3.4` -> `1.2.3.4`
    private val MAPPED_IPV4 =
        Regex("""^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$""", RegexOption.IGNORE_CASE)
}
