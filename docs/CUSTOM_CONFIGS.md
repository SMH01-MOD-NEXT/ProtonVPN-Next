# WARP and custom configurations

## Android UI

The Countries screen has a single lazy list on phones and tablets: saved **WARP first**, Proton countries in the middle, imported configurations last. Custom rows remain accessible during Proton loading/errors and without a Proton account. No load or availability metric is invented for WARP/custom rows. The side **+** menu opens WARP settings or the import dialog; there is no collapsible custom section or nested list.

**Settings → WARP → WARP** opens independent WARP settings with a same-named switch. Parameters are stored locally in a dedicated DataStore record, separate from Proton AWG settings. IP and port fields are read-only dropdowns with official values first and no official/experimental groups or random selection. “Custom endpoint” exposes an IPv4/DNS field. Current tunnel endpoint resolution is IPv4-only; IPv6-only custom endpoints are rejected before registration.

Profiles match ProtonVPN-Next-WEB:

| Profile | Native mode | Masking |
| --- | --- | --- |
| WireGuard | WireGuard | No AWG masking; Reserved retained |
| WARP #1 | AmneziaWG | Fixed reference QUIC I1 |
| WARP #2 | AmneziaWG | Fresh locally generated QUIC I1, SNI www.cloudflare.com |
| WARP #3 | AmneziaWG | Reference SIP I1 + I2 |

AWG profiles use Jc=4, Jmin=40, Jmax=70, S1–S4=0, H1–H4=1/2/3/4 and omit Reserved because the native AWG schema does not support it. No RandomTrailers or modified peer header numbers. Public packet presets come from https://warp-generation.github.io/script.js (2026-11-11); no credentials are imported. MASQUE, Xray, Clash and WireSock exports are WEB client formats, not additional native Android tunnel protocols.

The actual Cloudflare brand icon is Tabler `brand-cloudflare`, bundled as a 24dp monochrome vector with 1.5dp strokes matching the existing icon set. Source and full MIT attribution are in `docs/licenses/tabler-cloudflare.txt`.

## Singleton, secrets and proxy chains

Exactly one generated WARP entry is stored with stable identity `warp`. Successful generation atomically replaces its encrypted config; generation failure leaves the old config intact. Previously generated entries are conservatively recognized by their old name and configuration signature and collapsed; their old IDs remain aliases so saved proxy-chain references keep resolving. Other imported configurations are not renamed or collapsed. Regenerated parameters take effect on the next connection.

Private configurations are AES-GCM encrypted using a non-exportable Android Keystore key in `noBackupFilesDir/custom-configs.enc`. Plain local settings contain no keys. Import supports one-peer WireGuard/AmneziaWG `.conf` files and existing VLESS/VMess links. Parsing rejects shell hooks, unknown directives, duplicate fields, multiple peers and malformed keys/addresses/ports.

The **Proxy chain** add dialog lists WARP first and other saved configs below it. WARP settings also provide **Add as hop before Proton**. Entries store opaque `custom://warp`/`custom://<id>` references, never private config text. Regeneration preserves the WARP reference; deleted, unavailable or disabled WARP fails closed, not direct. Turning WARP off stops an active WARP connection or a Proton chain using it. Choosing WARP as the final VPN uses its own tunnel and does not apply the Proton chain to itself. Existing manually entered VLESS/VMess chain entries remain supported; max four hops and duplicate prevention remain in force. The last saved entry is the physical-network-facing hop in this application's chain model. WG/AWG hops need UDP support from any transport carrying them.

Import retains edit/delete/location overrides. Empty country/city fields trigger best-effort endpoint lookup through `https://freeipapi.com/api/json/<endpoint-IP>`; the import dialog discloses this. Fill both fields to skip detection. An endpoint location is not a verified VPN exit location. WARP uses its brand row rather than claiming a geographic exit.

## Registration and endpoints

X25519 private keys are created locally; only the public key is submitted to the fixed Cloudflare registration endpoint. The selected endpoint is configuration data, never an HTTP request URL. API bypass disabled means direct registration without inherited proxy/socket bypass. Netlify, Cloudflare, Deno and event mirrors require the fixed `/warp/reg` route from ProtonVPN-Next-WEB, deployed separately. ByeDPI/custom proxy retain their transport. Proton-only mirrors cannot relay WARP; unsupported bypass fails instead of silently switching to direct. Proton authenticators/interceptors are stripped, standard TLS validation is used, and response bodies are bounded before decoding.

`docs/warp-endpoints.json` records sources and their SHA-256. There are 396 public IPv4 suggestions (256 from official `162.159.193.0/24`, 140 community candidates) and 54 published ports. The four official UDP ports come first: 2408, 500, 1701, 4500. Blank/default IP preserves the registration response's endpoint. MASQUE ports and bot-only services are not mixed into WireGuard options. These are candidates, not 21,384 handshake-verified IP/port combinations or a claim of all possible ports.

## Verification

- Unit tests: endpoint ordering/validation, all three packet profiles, local preferences serialization, singleton replacement, legacy aliases, limits and fail-closed references, plus the existing suites.
- Android instrumented tests use fixtures only, with **no live registration or VPN sockets**. Native Libbox checks all WARP profiles as intermediate hops. Compose checks dropdown selection, custom-endpoint input, visible unified rows, side + menu and WARP selection in the chain editor.
- A disposable encrypted file verifies concurrent replacement, reload, secret-at-rest protection and disabled-hop rejection on a real Android Keystore. Settings persistence is restored to its prior value after the test.
- Tests run in the separate nightlyStandard debug application, not the user's installed nightlyPrivacy application. Device UI/native checks are not a registered WARP handshake. Real connectivity still needs testing on the target network. No push, mirror deployment or live WARP account creation is part of this change.


### IPv4-only chain startup regression

The 2026-11-11 native patch preserves IPv6-disabled operation. AWG's optional
IPv6 bind no longer makes IPv4-only WARP hops fail with “missing IPv6 local
address”. Numeric standard-WG hops are also ready before AWG opens its detour
bind. Missing/fatal hops still fail closed; no direct fallback is added.
`WarpChainStartupInstrumentedTest` starts the real service with all four native
profiles using loopback fixture peers and no TUN. A new APK containing the
rebuilt core is needed; simply regenerating a WARP config is not the fix.
