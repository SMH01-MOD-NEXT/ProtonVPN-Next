/**
 * Netlify proxy for the Proton API, with CORS enabled for the website.
 *
 * The browser cannot talk to vpn-api.proton.me directly: the API sends no
 * `access-control-allow-origin` and rejects preflight. This function forwards
 * the request, answers OPTIONS itself and strips the response headers that stop
 * a page from reading the body.
 *
 * Deploy: `netlify deploy --prod` from `proxy/netlify`.
 */

/**
 * Origins allowed to read proxied responses.
 *
 * Patterns rather than a fixed list: the site is reachable through the apex
 * domain, subdomains, Cloudflare preview deployments and local dev servers on
 * arbitrary ports, and an origin missing from a hardcoded list fails in a way
 * that looks exactly like a broken proxy.
 */
const ALLOWED_ORIGIN_PATTERNS = [
	/^https:\/\/([a-z0-9-]+\.)*protonnext\.qzz\.io$/,
	/^https:\/\/[a-z0-9-]+\.workers\.dev$/,
	/^https:\/\/[a-z0-9-]+\.pages\.dev$/,
	/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/,
]

function isAllowedOrigin(origin) {
	return ALLOWED_ORIGIN_PATTERNS.some((pattern) => pattern.test(origin))
}

const UPSTREAMS = [
	{ prefix: "/verify-api", host: "https://verify-api.proton.me" },
	{ prefix: "/verify", host: "https://verify.proton.me" },
]
const DEFAULT_UPSTREAM = "https://vpn-api.proton.me"

// Headers Proton sets that would either break the page or leak nothing useful.
const STRIPPED_RESPONSE_HEADERS = new Set([
	"content-security-policy",
	"content-security-policy-report-only",
	"x-frame-options",
	"content-encoding",
	"content-length",
	"transfer-encoding",
	"connection",
	"set-cookie",
])

const FORWARDED_REQUEST_HEADERS = [
	"accept",
	"authorization",
	"content-type",
	"x-pm-appversion",
	"x-pm-apiversion",
	"x-pm-uid",
	"x-pm-locale",
	"x-pm-human-verification-token",
	"x-pm-human-verification-token-type",
	"user-agent",
]

function corsHeaders(origin) {
	const headers = {
		"access-control-allow-methods": "GET, POST, PUT, DELETE, OPTIONS",
		"access-control-allow-headers": FORWARDED_REQUEST_HEADERS.join(", "),
		"access-control-max-age": "86400",
		vary: "Origin",
	}
	// Echo the caller's origin, never a substitute. Answering an unknown origin
	// with an allowed one produced the confusing "does not match" browser error
	// instead of a plain "origin not allowed".
	if (isAllowedOrigin(origin)) {
		headers["access-control-allow-origin"] = origin
	}
	return headers
}

function resolveUpstream(pathname) {
	for (const upstream of UPSTREAMS) {
		if (pathname === upstream.prefix || pathname.startsWith(`${upstream.prefix}/`)) {
			return `${upstream.host}${pathname.slice(upstream.prefix.length) || "/"}`
		}
	}
	return `${DEFAULT_UPSTREAM}${pathname}`
}

export default async function handler(request) {
	const origin = request.headers.get("origin") ?? ""
	const cors = corsHeaders(origin)

	if (request.method === "OPTIONS") {
		return new Response(null, { status: 204, headers: cors })
	}

	const incoming = new URL(request.url)
	const target = `${resolveUpstream(incoming.pathname)}${incoming.search}`

	const headers = new Headers()
	for (const name of FORWARDED_REQUEST_HEADERS) {
		const value = request.headers.get(name)
		if (value) headers.set(name, value)
	}

	let upstreamResponse
	try {
		upstreamResponse = await fetch(target, {
			method: request.method,
			headers,
			body: ["GET", "HEAD"].includes(request.method)
				? undefined
				: await request.text(),
			redirect: "follow",
		})
	} catch (error) {
		return new Response(
			JSON.stringify({ Code: 0, Error: `Upstream unreachable: ${error}` }),
			{ status: 502, headers: { ...cors, "content-type": "application/json" } },
		)
	}

	const responseHeaders = new Headers(cors)
	for (const [name, value] of upstreamResponse.headers) {
		const lower = name.toLowerCase()
		if (STRIPPED_RESPONSE_HEADERS.has(lower)) continue
		// Proton sets its own CORS headers on some endpoints; copying them would
		// overwrite ours and reject the page.
		if (lower.startsWith("access-control-")) continue
		responseHeaders.set(name, value)
	}

	return new Response(await upstreamResponse.arrayBuffer(), {
		status: upstreamResponse.status,
		headers: responseHeaders,
	})
}

export const config = { path: "/*" }
