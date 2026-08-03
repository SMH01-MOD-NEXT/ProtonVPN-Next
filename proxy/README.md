# API proxies

The config generator runs entirely in the browser, but `vpn-api.proton.me`
sends no CORS headers and rejects preflight, so a page can never read its
responses. This proxy forwards the request and adds the missing headers.

Cloudflare is intentionally **not** used for Proton API traffic: Proton
throttles it far more aggressively than the alternatives. The Cloudflare worker
on the `proxy` branch stays in place for other purposes only.

## What the proxies do

- answer `OPTIONS` preflight with `204`
- add `Access-Control-Allow-Origin/Methods/Headers`
- strip `content-security-policy` and `x-frame-options` from Proton's response
- drop `set-cookie`, so no Proton session cookie is ever handed to the page
- route by path prefix:
  - `/verify-api*` → `verify-api.proton.me`
  - `/verify*` → `verify.proton.me`
  - everything else → `vpn-api.proton.me`

Only a fixed allowlist of request headers is forwarded (`authorization`,
`x-pm-uid`, `x-pm-appversion`, …), so a misconfigured client cannot smuggle
extra headers upstream.

These sources live on `protonvpn-next-dev`, the default branch, on purpose:
Deno Deploy builds the default branch of a linked repository and offers no
branch picker, so keeping the proxy on a feature branch would make it
undeployable. The Android client uses the same proxy, so this is also the
branch that owns it.

## Allowed origins

Origins are matched by pattern, not by a fixed list, because the site is
served from several hosts (`home.protonnext.qzz.io` today) and because a
missing entry fails in a way that looks like a broken proxy:

- `https://` + any subdomain of `protonnext.qzz.io`
- `https://<name>.workers.dev` and `https://<name>.pages.dev` previews
- `http://localhost` and `http://127.0.0.1` on any port

An origin outside the patterns gets no `Access-Control-Allow-Origin` at all.
The proxy never answers with a substituted origin: doing so made browsers
report that the header "does not match", which hides the real cause.

Changing a proxy URL means updating every consumer:

- `src/lib/api.js` (`API_ENDPOINTS`) on the `website` branch
- `app/src/main/java/ru/protonmod/next/di/NetworkModule.kt`
  (`PROTON_PROXY_DENO_URL`; `PROTON_PROXY_NETLIFY_URL` still exists as an app
  bypass strategy, but no Netlify deployment backs it any more)
- `app/src/main/java/ru/protonmod/next/data/network/TokenAuthenticator.kt`
- `app/src/main/java/ru/protonmod/next/data/network/DohFallbackInterceptor.kt`
- `app/src/main/java/ru/protonmod/next/ui/screens/CaptchaScreen.kt`

Host suffix checks (`*.deno.net`) keep working on their own.

## Netlify (retired)

The Netlify proxy was removed. Two reasons, either of which is fatal:

- Netlify is not reachable from Russia, where most of the users are, so it
  never served as a fallback for them.
- The GitHub account the site `shimmering-stroopwafel-51675e` was linked to has
  been banned, so the deployment cannot be updated from a repository at all.

The site is stuck on a build that predates the CORS support and answers every
browser request without `Access-Control-Allow-Origin`. Delete it rather than
leaving a broken endpoint reachable.

## Deno Deploy

Current deployment: `https://protonvpn-next-mirror-yq0w6dbkxg4j.smh01-mirrors.deno.net`

With the CLI:

```sh
cd proxy/deno
deployctl deploy --project=protonvpn-next-mirror main.ts
```

Without the CLI, link the project to the repository in the Deno Deploy
dashboard and set the entrypoint to `proxy/deno/main.ts` on branch `website`.
No build step and no environment variables are required.

## After deploying

Both URLs are listed in `src/lib/api.js` (`API_ENDPOINTS`) and are tried in
order; the second one is used when the first is unreachable or blocked. When an
origin changes, update `ALLOWED_ORIGINS` in **both** proxies.

Verify CORS is live:

```sh
curl -i -X OPTIONS \
  -H 'Origin: https://protonnext.qzz.io' \
  -H 'Access-Control-Request-Method: POST' \
  <proxy-url>/vpn/v2/logicals
```

The response must contain `access-control-allow-origin`.
