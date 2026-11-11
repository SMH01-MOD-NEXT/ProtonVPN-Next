# WARP and custom configurations

Countries → Custom configurations supports one-peer WireGuard/AmneziaWG `.conf` files and existing VLESS/VMess links. Import, editing, deletion and WARP generation use the existing app cards, buttons and dialogs; no Proton load or availability metrics are fabricated.

Config payloads are encrypted with Android Keystore AES-GCM in `noBackupFilesDir`. Saved-config chain entries use opaque `custom://` references; deleting a referenced entry causes a connection error rather than a silent direct fallback. Existing manually entered VLESS/VMess chain entries are unchanged.

Blank country/city fields are best-effort endpoint geolocation through `https://freeipapi.com/api/json/<endpoint-IP>`. The user is informed before saving; filling both fields skips the lookup. An endpoint location is not a verified VPN exit location, particularly for WARP/anycast. Failure leaves missing fields editable.

WARP generates X25519 keys on the device and submits only the public key. Disabling API bypass forces direct registration without inheriting a proxy/socket bypass. Netlify, Cloudflare, Deno and event mirrors require the new fixed `/warp/reg` endpoint from ProtonVPN-Next-WEB; they must be deployed separately. ByeDPI/custom proxy use the selected client transport to the WARP API. Proton-only mirrors cannot relay WARP; registration fails rather than silently falling back to a direct request. Select a supported bypass or explicitly disable API bypass.

Standard WireGuard is now included in the pinned native build because WARP requires its `reserved` peer bytes. AWG uses its own endpoint schema and rejects reserved bytes instead of silently discarding them. Imported configs reject shell hooks, arbitrary keys/sections, multiple peers and malformed keys, endpoints or CIDRs. The app retains its existing IPv4-only routing policy; IPv6-only endpoints are not supported. WG/AWG hops need UDP support through any preceding transport.

For a Proton connection, add a saved config using its edit dialog → Add as hop before Proton. The last entry is the physical-network-facing hop. Resolve saved references before validation/preflight; resolve peer hostnames on the underlying network, not through a tunnel that has not started.

Verification: stableStandardDebug and nightlyPrivacyDebug builds/unit tests; parser tests cover WARP reserved bytes, AWG preshared keys, unsafe import rejection and mixed chains. Real registration/tunnel/device UI must also be checked on an Android device before release. No device was connected during implementation.

## Endpoint and AWG2 catalog (2026-11-11)

`docs/warp-endpoints.json` records source URLs and the source script SHA-256. Cloudflare officially documents WireGuard IPv4 `162.159.193.0/24`, IPv6 `2606:4700:100::/48` and UDP 2408 / 500 / 1701 / 4500. Before login, `162.159.192.0/24` can also appear. The public reference generator lists 54 ports and 14 IPv4 prefixes, but picks only last octets 1–10, not every address in each /24. This yields 140 community candidates plus the 256-address official range (396 IPv4 suggestions). None is labelled handshake-tested. The Android app remains IPv4-only.

Generation now accepts an IPv4 candidate, any of the 54 published ports, and an optional experimental AWG2/QUIC profile. A blank IP preserves the endpoint returned by registration. Unknown candidate IPs/ports fail before registration; arbitrary custom endpoints can still be imported in a custom config. MASQUE ports and bot-gated/nonstandard service endpoints are not treated as public WireGuard endpoints.

AWG2 profile: Jc=4, Jmin=40, Jmax=70, S1=S2=S3=S4=0, H1=1/H2=2/H3=3/H4=4. I1 is generated locally with the existing QUIC Initial generator and SNI `www.cloudflare.com`, with fresh randomness per generation. This is not MASQUE. Like the reference AWG2 export, this format omits Reserved (the embedded AWG schema does not support it). Standard WG mode preserves Reserved. This difference is disclosed in the generation UI and needs a real registered handshake on the target network; no compatibility guarantee is made. Do not arbitrarily enable RandomTrailers or change WireGuard header numbers against a standard Cloudflare peer.

A UDP send, TCP connect or ICMP response does not prove a working WARP tunnel. No live account/registered handshake was created during these tests.
