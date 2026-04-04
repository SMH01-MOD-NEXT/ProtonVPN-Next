/**
 * Proton VPN-Next Cloudflare Worker Proxy
 * Handles proxying for Proton API and Human Verification (Captcha).
 */

export interface Env {}

export default {
	async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
		const url = new URL(request.url);
		const path = url.pathname;

		let targetHost: string;

		// Route to appropriate Proton service based on path prefix
		if (path.startsWith('/verify-api')) {
			targetHost = 'verify-api.proton.me';
			url.pathname = path.replace('/verify-api', '');
		} else if (path.startsWith('/verify')) {
			targetHost = 'verify.proton.me';
			url.pathname = path.replace('/verify', '');
		} else {
			targetHost = 'vpn-api.proton.me';
		}

		// Prepare new URL for the target
		url.host = targetHost;
		url.protocol = 'https:';

		// Clone headers and remove host-specific ones that might interfere
		const newHeaders = new Headers(request.headers);
		newHeaders.delete('host');
		newHeaders.delete('cf-connecting-ip');
		newHeaders.delete('cf-worker');
		newHeaders.delete('cf-ray');
		newHeaders.delete('cf-visitor');

		// Create the proxied request
		const proxyRequest = new Request(url.toString(), {
			method: request.method,
			headers: newHeaders,
			body: request.body,
			redirect: 'manual',
		});

		try {
			const response = await fetch(proxyRequest);

			// Clone response to modify headers
			const modifiedResponse = new Response(response.body, response);

			// Ensure CORS is allowed for Captcha WebView interactions if needed
			modifiedResponse.headers.set('Access-Control-Allow-Origin', '*');
			modifiedResponse.headers.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
			modifiedResponse.headers.set('Access-Control-Allow-Headers', '*');

			// Remove security headers that might block loading in WebView via proxy
			modifiedResponse.headers.delete('content-security-policy');
			modifiedResponse.headers.delete('x-frame-options');

			return modifiedResponse;
		} catch (e) {
			return new Response(`Proxy Error: ${e}`, { status: 502 });
		}
	},
};
