# WARP and custom configurations

Countries → Custom configurations supports one-peer WireGuard/AmneziaWG `.conf` files and existing VLESS/VMess links. Import, editing, deletion and WARP generation use the existing app cards, buttons and dialogs; no Proton load or availability metrics are fabricated.

Config payloads are encrypted with Android Keystore AES-GCM in `noBackupFilesDir`. Saved-config chain entries use opaque `custom://` references; deleting a referenced entry causes a connection error rather than a silent direct fallback. Existing manually entered VLESS/VMess chain entries are unchanged.

Blank country/city fields are best-effort endpoint geolocation through `https://freeipapi.com/api/json/<endpoint-IP>`. The user is informed before saving; filling both fields skips the lookup. An endpoint location is not a verified VPN exit location, particularly for WARP/anycast. Failure leaves missing fields editable.

WARP generates X25519 keys on the device and submits only the public key. Disabling API bypass forces direct registration without inheriting a proxy/socket bypass. Netlify, Cloudflare, Deno and event mirrors require the new fixed `/warp/reg` endpoint from ProtonVPN-Next-WEB; they must be deployed separately. ByeDPI/custom proxy use the selected client transport to the WARP API. Proton-only mirrors cannot relay WARP; registration fails rather than silently falling back to a direct request. Select a supported bypass or explicitly disable API bypass.

Standard WireGuard is now included in the pinned native build because WARP requires its `reserved` peer bytes. AWG uses its own endpoint schema and rejects reserved bytes instead of silently discarding them. Imported configs reject shell hooks, arbitrary keys/sections, multiple peers and malformed keys, endpoints or CIDRs. The app retains its existing IPv4-only routing policy; IPv6-only endpoints are not supported. WG/AWG hops need UDP support through any preceding transport.

For a Proton connection, add a saved config using its edit dialog → Add as hop before Proton. The last entry is the physical-network-facing hop. Resolve saved references before validation/preflight; resolve peer hostnames on the underlying network, not through a tunnel that has not started.

Verification: stableStandardDebug and nightlyPrivacyDebug builds/unit tests; parser tests cover WARP reserved bytes, AWG preshared keys, unsafe import rejection and mixed chains. Real registration/tunnel/device UI must also be checked on an Android device before release. No device was connected during implementation.
