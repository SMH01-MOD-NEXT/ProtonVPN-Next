/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.data.network

import okhttp3.HttpUrl
import ru.protonmod.next.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Single authoritative registry used by API routing and the IP-service catalogue. */
object ProxyEndpoints {
    const val NETLIFY_URL = "https://shimmering-stroopwafel-51675e.netlify.app/"
    const val CLOUDFLARE_URL = "https://protonvpn-next-web.smh01.workers.dev/api"
    const val CLOUDFLARE_SITE_URL = "https://home.protonnext.qzz.io/"
    const val DENO_URL = "https://protonvpn-next-web--main.smh01-mirrors.deno.net/api/"
    data class BuiltIn(val id: String, val titleRes: Int, val bases: List<String>, val locationApi: Boolean = false)
    val builtIn = listOf(
        BuiltIn("netlify", R.string.api_bypass_strategy_netlify, listOf(NETLIFY_URL), locationApi = true),
        BuiltIn("cloudflare", R.string.ip_lookup_cloudflare, listOf(CLOUDFLARE_SITE_URL, CLOUDFLARE_URL)),
        BuiltIn("deno", R.string.ip_lookup_deno, listOf(DENO_URL)),
    )

    fun httpsBase(raw: String): HttpUrl? = raw.trim().toHttpUrlOrNull()?.takeIf {
        it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null
    }

    /** Config can explicitly declare query routing; legacy Vercel entries remain compatible. */
    fun queryRouting(base: HttpUrl, routing: String = "auto"): Boolean = when (routing) {
        "query" -> true
        "path" -> false
        else -> base.queryParameter("__path") != null || base.host == "vercel.app" || base.host.endsWith(".vercel.app")
    }

    fun apiUrl(original: HttpUrl, base: HttpUrl, routing: String = "auto"): HttpUrl {
        val prefix = when (original.host) {
            "verify-api.proton.me" -> "/verify-api"
            "verify.proton.me" -> "/verify"
            else -> ""
        }
        val target = prefix + original.encodedPath
        val builder = base.newBuilder().query(null).fragment(null)
        if (queryRouting(base, routing)) {
            builder.encodedPath(base.encodedPath.trimEnd('/').ifEmpty { "/api" })
            builder.addQueryParameter("__path", target)
        } else {
            builder.encodedPath(base.encodedPath.trimEnd('/') + target)
        }
        // Preserve target query parameters without mixing them into the routing path.
        for (index in 0 until original.querySize) {
            val key = original.queryParameterName(index)
            if (key != "__path") builder.addQueryParameter(key, original.queryParameterValue(index))
        }
        return builder.build()
    }

    /** Try both echo contracts on the SAME permitted host, never another provider. */
    fun echoUrls(base: HttpUrl, routing: String = "auto"): List<String> {
        val direct = base.newBuilder().encodedPath("/__proxy/whoami").query(null).build()
        val query = base.newBuilder().encodedPath(
            base.encodedPath.trimEnd('/').ifEmpty { "/api" }
        ).query(null).addQueryParameter("__path", "/__proxy/whoami").build()
        return if (queryRouting(base, routing)) listOf(query.toString(), direct.toString())
        else listOf(direct.toString(), query.toString())
    }
}
