# TASK_PLAN.md — Censorship-resistant DNS rework (Android)

## Goal
Roskomnadzor now (a) redirects plaintext DNS (UDP/TCP 53) to NSDI block-page
answers and (b) blocks DoH/DoT. Make the Android client resolve names reliably
without ever trusting a Russian resolver.

Hard constraints from the user:
- **Never** use Russian DNS providers (Yandex, SkyDNS, Comss.one, ISP resolvers).
- Keep **Cloudflare** working by any means possible.
- Keep **Google**, and add other reputable EU/US resolvers — no no-name services.
- Android client only for now (WEB is a separate track).

## Root cause found during discovery
`di/NetworkModule.kt` (`dynamicDns`, ~line 434) resolves like this when the API
bypass is off:

```kotlin
try { result.addAll(Dns.SYSTEM.lookup(hostname)) }      // system DNS FIRST
catch (e: Exception) { result.addAll(doh.lookup(hostname)) }  // DoH only on THROW
```

An NSDI redirect does not throw — it returns a syntactically valid A record
pointing at the block page. So the poisoned answer is accepted and DoH is never
reached. The fallback is structurally unable to fire for this attack.

Secondary problems:
- `data/network/DohClient.kt` hardcodes `dns.google` / `cloudflare-dns.com` as
  **hostnames** and resolves them with a plain `OkHttpClient`, i.e. through the
  hijacked system resolver. The DoH client is defeated before its first query.
- `buildDnsOverHttps()` has exactly one provider (Cloudflare). No DoT, no
  second opinion, no recovery if `cloudflare-dns.com:443` is DPI-blocked by SNI.

## Design
1. **Resolve DoH endpoints by IP literal**, not hostname
   (`https://1.1.1.1/dns-query`, `https://8.8.8.8/dns-query`, ...). This removes
   both the system-resolver dependency and the SNI hostname DPI keys on.
2. **DoH first, always.** System DNS drops to last-resort and is only trusted
   when a hijack canary says the local resolver is honest.
3. **Hijack canary:** resolve a random `*.invalid` name through the system
   resolver. A truthful resolver returns NXDOMAIN; a hijacking one returns an
   address. Detects NSDI without hardcoding block-page IPs, which rotate.
4. **DoT (853) fallback** for when 443 DoH is throttled or blocked.
5. **Curated non-RU provider registry** with an explicit RU denylist so a
   Russian resolver cannot be reintroduced by accident or by user input.

## Tasks
- [x] Inspect the network stack and locate the hijack-blind fallback
- [x] `data/network/dns/DnsProviders.kt` — curated non-RU registry + RU denylist
- [x] `data/network/dns/HijackGuard.kt` — NSDI/poisoning canary detection
- [x] `data/network/dns/DotClient.kt` — RFC 7858 DNS over TLS transport (port 853)
- [x] `data/network/dns/SecureDnsResolver.kt` — DoH(IP) -> DoT -> guarded system
- [x] Rewire `di/NetworkModule.kt` `dynamicDns` to DoH-first ordering
- [x] Rewire `data/network/DohClient.kt` onto IP-literal endpoints + secure resolver
- [x] `ui/screens/CaptchaScreen.kt` — address its private DoH client by IP literal
- [x] `SettingsManager`: provider + DoT preferences; reject RU custom DNS at the store
- [x] Localise new strings (en + ru; remaining locales fall back to en via Crowdin)
- [/] Unit tests: canary logic, provider ordering, RU denylist enforcement
- [ ] Settings UI: provider picker + DoT toggle + rejection warning
- [ ] `./gradlew assembleDebug` + `testStableStandardDebugUnitTest` green
- [ ] Atomic Conventional Commits

## Build note
The project has product flavors, so bare `compileDebugKotlin` is ambiguous.
Use a concrete variant, e.g. `:app:compileStableStandardDebugKotlin`.
`:app:compileStableStandardDebugKotlin` passes as of the DNS rework.

## Non-goals
- Desktop/WEB DNS changes (explicitly deferred by the user).
- Shipping a bundled recursive resolver.
