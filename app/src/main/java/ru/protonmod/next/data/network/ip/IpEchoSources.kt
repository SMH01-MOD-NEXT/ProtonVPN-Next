/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network.ip

import ru.protonmod.next.R
import java.security.MessageDigest
import ru.protonmod.next.data.network.ProxyEndpoints
import ru.protonmod.next.data.model.eventbypass.EventBypassCache
import ru.protonmod.next.data.model.eventbypass.EventBypassEntry

/** UI and requests share this catalogue. No separate hard-coded Vercel/event providers. */
object IpEchoSources {
    val DEFAULT_ENABLED_IDS: Set<String> = setOf("proton", "api.myip.com", "freeipapi")
    enum class Kind { PROTON, BUILT_IN, EVENT, PUBLIC }
    data class Service(
        val id: String, val kind: Kind, val name: String = "", val urls: List<String>,
        val isOwn: Boolean = false, val canNameCountry: Boolean = true, val titleRes: Int = 0,
    )
    data class Source(val id: String, val url: String, val isOwn: Boolean)

    private val publicServices = listOf(
        Service("proton", Kind.PROTON, titleRes = R.string.ip_lookup_proton, urls = listOf("https://api.protonvpn.ch/vpn/v1/location")),
        Service("api.myip.com", Kind.PUBLIC, titleRes = R.string.ip_lookup_myip, urls = listOf("https://api.myip.com")),
        Service("freeipapi", Kind.PUBLIC, titleRes = R.string.ip_lookup_freeipapi, urls = listOf("https://freeipapi.com/api/json")),
    )

    fun catalogue(eventsJson: String = "", selectedUrl: String = "", selectedName: String = ""): List<Service> {
        val builtIn = ProxyEndpoints.builtIn.map { endpoint ->
            val bases = endpoint.bases.mapNotNull(ProxyEndpoints::httpsBase)
            val urls = bases.flatMap { base ->
                if (endpoint.locationApi) listOf(base.newBuilder().encodedPath("/vpn/v1/location").build().toString())
                else ProxyEndpoints.echoUrls(base)
            }.distinct()
            Service(endpoint.id, Kind.BUILT_IN, urls = urls, isOwn = true, canNameCountry = endpoint.id != "deno", titleRes = endpoint.titleRes)
        }.filter { it.urls.isNotEmpty() }
        val builtInHosts = ProxyEndpoints.builtIn.flatMap { it.bases }.mapNotNull(ProxyEndpoints::httpsBase).map { it.host }.toSet()
        val events = EventBypassCache.decode(eventsJson).filter { it.normalizedUrl().isNotEmpty() }
        // Version-1/legacy installs may only have a selected URL. Never resurrect a retired
        // endpoint when a nonempty published cache explicitly says it is unavailable.
        val entries = if (eventsJson.isBlank()) listOf(EventBypassEntry(
            name = selectedName, url = selectedUrl, enabled = true,
        )) else events
        val dynamic = entries.mapNotNull { entry ->
            val base = ProxyEndpoints.httpsBase(entry.normalizedUrl()) ?: return@mapNotNull null
            if (base.host in builtInHosts) return@mapNotNull null
            val urls = ProxyEndpoints.echoUrls(base, entry.routing)
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(base.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            Service("event:$fingerprint", Kind.EVENT, entry.name.trim().ifBlank { base.host }, urls, isOwn = true)
        }.distinctBy { it.id }
        return listOf(publicServices.first()) + builtIn + dynamic + publicServices.drop(1)
    }

    /** Legacy generic event/vercel consent must not silently authorize a rotating host. */
    fun currentEnabled(ids: Set<String>, services: List<Service>): Set<String> =
        (ids - setOf("event", "vercel", "cloudflare-site") + if ("cloudflare-site" in ids) setOf("cloudflare") else emptySet())
            .intersect(services.map { it.id }.toSet())

    fun selected(isRussianRegion: Boolean, services: List<Service>, enabledIds: Set<String>): List<Source> {
        val enabled = currentEnabled(enabledIds, services)
        val available = services.filter { it.id in enabled }
        val ranked = if (isRussianRegion) available.sortedBy {
            when { it.id == "proton" -> 0; it.id == "deno" -> 1; it.kind == Kind.EVENT -> 2; it.isOwn -> 3; else -> 4 }
        } else available
        return ranked.flatMap { service -> service.urls.map { Source(service.id, it, service.isOwn) } }
    }

    fun selectedCountryProbes(isRussianRegion: Boolean, services: List<Service>, enabledIds: Set<String>): List<Source> =
        selected(isRussianRegion, services.filter { it.isOwn && it.canNameCountry }, enabledIds)

    fun normaliseAddress(raw: String): String {
        var address = raw.trim()
        if (address.isEmpty()) return ""

        BRACKETED.matchEntire(address)?.let { address = it.groupValues[1] }
        WITH_PORT.matchEntire(address)?.let { address = it.groupValues[1] }
        MAPPED_IPV4.matchEntire(address)?.let { address = it.groupValues[1] }

        return address
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
