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

const ALLOWED_ORIGINS = [
	"https://protonnext.qzz.io",
	"https://www.protonnext.qzz.io",
	"http://localhost:5173",
	"http://127.0.0.1:5173",
]

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
	const allowed = ALLOWED_ORIGINS.includes(origin) ? origin : ALLOWED_ORIGINS[0]
	return {
		"access-control-allow-origin": allowed,
		"access-control-allow-methods": "GET, POST, PUT, DELETE, OPTIONS",
		"access-control-allow-headers": FORWARDED_REQUEST_HEADERS.join(", "),
		"access-control-max-age": "86400",
		vary: "Origin",
	}
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
		if (STRIPPED_RESPONSE_HEADERS.has(name.toLowerCase())) continue
		responseHeaders.set(name, value)
	}

	return new Response(await upstreamResponse.arrayBuffer(), {
		status: upstreamResponse.status,
		headers: responseHeaders,
	})
}

export const config = { path: "/*" }
