# Sentry triage & fix plan (excluding anti-tamper / ISP DPI issues)

Org: `protonvpn-next` (android, cli, desktop). Snapshot: 2026-08-03, `is:unresolved`, last 90d.

## 0. Excluded by request (anti-tamper / provider-side DPI & MITM)

Not code bugs on our side — blocking/interference by the user's ISP, or anti-tamper subsystem:

| Issue | Title | Reason |
|---|---|---|
| ANDROID-1ZJ | Proxy connection failed ... ECONNREFUSED | DPI / local proxy blocked |
| ANDROID-1ZK | Retry for vpn-api.proton.me failed even with DoH fallback | DPI / DNS+IP blocking |
| ANDROID-1AQ, 1H3, 21H, 22C | SSLPeerUnverifiedException: Certificate pinning failure | ISP MITM |
| ANDROID-1H9, 235 | Hostname vpn-api.proton.me not verified | ISP MITM |
| ANDROID-217, 21F | SSLHandshakeException / Unable to parse TLS packet header | DPI TLS mangling |
| ANDROID-20M | WebView ERR_NAME_NOT_RESOLVED | DNS blocking |
| ANDROID-21G | Connect & Go: timed out waiting for VPN | downstream of DPI blocking |
| ANDROID-223 | Background ANR in `AntiTamperBridge.invokeNative` | anti-tamper |
| ANDROID-21K | "Potential Security Risk" (/obfuscation_settings) | anti-tamper/obfuscation |
| ANDROID-214, 21J | Background ANR in `MirrorTrustManager.getAcceptedIssuers` | mirror/TLS bypass path |

## 1. In scope — real code defects

### P0 — crashes with a clear root cause
1. **ANDROID-232 / ANDROID-21S** — `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported`
   - `ui/screens/WelcomeScreen.kt:441`, `painterResource(R.drawable.vpn_welcome_globe)` on a `.webp` asset that Compose routes into `loadVectorResource`.
   - Fix: load the raster asset safely (`ImageBitmap`/`BitmapFactory`-backed painter) or ship a real vector, plus a guarded fallback so onboarding never crashes.
2. **CLI-4** — `UnboundLocalError: 'args'` in `do_connect` (release 1.0.0, monolithic `pvpn-next.py`)
   - Current modular `pvpn_cli/cli/commands/connection.py` already defines `args` in both branches → verify and close as fixed in the refactor; add a regression test.
3. **DESKTOP-2** — `TypeError: Cannot read properties of undefined (reading 'rx')` — traffic-stats object read before the first engine tick; add null-safe defaults.
4. **ANDROID-221** — `IllegalStateException: Cannot bind ru.protonmod.next.vpn.ProtonVpnService`.

### P1 — service / lifecycle correctness
5. **ANDROID-21T** — `Bad notification for startForeground`.
6. **ANDROID-21N** — `ForegroundServiceDidNotStartInTimeException`.
7. **ANDROID-21R** — `SecurityException: ... does not belong to uid` in `onStartCommand`.
8. **ANDROID-22A / ANDROID-233** — `create ipv4 connection: no available network interface` (tunnel start while no usable network) → retry/backoff + user-facing error string.
9. **ANDROID-20P** — `BackendException` in `onStartCommand`.

### P2 — data / UX defects
10. **ANDROID-228** — `SQLiteDatabaseLockedException (SQLITE_BUSY)` → WAL + single writer / busy timeout.
11. **ANDROID-1ZV** — "Cannot connect: Server list is empty" → force refresh + friendly error instead of exception.
12. **ANDROID-1ZX** — Large HTTP payload on `/countries` → pagination/compression/trimmed fields.
13. **ANDROID-234** — Unsuccessful response `402` → handle payment-required as a domain state.
14. **DESKTOP-1** — `prompt() is not supported` in Electron → replace with in-app dialog component.
15. **CLI-8 / CLI-9 / CLI-A** — `KeyboardInterrupt` reported as errors → handle Ctrl+C gracefully and stop sending to Sentry.

### P3 — ANRs needing profiling before a fix
16. ANDROID-1YB (`SessionRefreshWorker.schedule`), 20V (`NetworkModule.provideOkHttpClient`), 20R/222 (MainActivity), 225 (`ProtonNextApp.onCreate`), 229/21Q (/home), 21X (`PiiScrubber.isConfigBlock`), 230, 22W.
17. ANDROID-192 — Regex on main thread (/home); ANDROID-219 / 21Y — blocking operations on /home and /welcome.

## 2. Working method per fix

1. Read the issue details in Sentry (stack, release, device tags).
2. Locate and read the code, make a minimal fix consistent with project style.
3. No hardcoded user-facing strings — add to `strings.xml` / i18n files.
4. Build (`./gradlew assembleDebug`, desktop/CLI build) and run relevant tests.
5. Commit with `fix:` / `ui:` / `chore:` prefix and `Fixes <ISSUE-ID>` in the body.
6. Resolve the issue in Sentry after the fix ships.

## 3. Status

- [x] Triage completed
- [ ] P0 batch
- [ ] P1 batch
- [ ] P2 batch
- [ ] P3 batch (needs profiling data)
