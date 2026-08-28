# AWGBox transport architecture

## Goal

Replace the dedicated AmneziaWG Android backend with an extensible sing-box core
that still supports AWG/AWG2. This makes future censorship-resistance work
possible without replacing Android's VPN lifecycle again.

## Selected core

The integration uses `hoaxisr/amnezia-box` `1.14.0-rc.1-awgm.14` because it:

- tracks the sing-box 1.14 release line;
- embeds `amneziawg-go`;
- exposes AWG 1.x and AWG2 fields (`Jc`, `Jmin/Jmax`, `S1-S4`, `H1-H4`, `I1-I5`);
- provides gomobile `libbox` bindings for Android;
- fixes AWG endpoint DNS handling in sing-box routing.

The binary is pinned and reproducible rather than downloaded during a normal app
build. Upgrading the core must be a deliberate review because it is part of the
trusted network boundary.

## Runtime layers

1. `AmneziaVpnManager` remains the UI/application facade for now. Its name is
   retained to avoid a high-risk, unrelated public API rename in the first core
   migration.
2. `AwgBoxConfigGenerator` translates Proton server/session data and existing
   obfuscation settings into sing-box JSON.
3. `ProtonVpnService` owns `CommandServer`, lifecycle, reconnect behavior,
   notifications and traffic reporting.
4. `AwgBoxPlatform` implements libbox's Android callbacks: TUN creation, socket
   protection, app routing, interface discovery and default-network monitoring.
5. `VpnTunnelState` isolates all UI code from any particular engine's state type.

## Configuration topology

```text
Android apps
    -> sing-box TUN inbound (mixed stack, strict route)
    -> sing-box router and DNS hijack
    -> AWG endpoint (amneziawg-go)
    -> Proton VPN server
```

The AWG endpoint is represented as an `endpoint`, not a legacy outbound. That
allows sing-box to use it as the final route while retaining DNS and route-rule
extensibility.

## Minimal mobile build

The embedded core is compiled only with `with_awg` and `with_utls` optional
features, plus `with_clash_api` because libbox CommandServer requires its internal connection tracker. VLESS, VMess, SOCKS/HTTP and proxy chaining are part of the base core.
QUIC protocols (Hysteria2/TUIC), gVisor, standard WireGuard, Tailscale, Naive
outbound are omitted. No external Clash controller is configured. Android's system TUN stack replaces gVisor.
This keeps VLESS/Reality client compatibility while substantially reducing the
native library size.

## Proxy chaining

The client accepts trusted `vless://` and base64 `vmess://` share links. One link
per line creates up to four ordered hops. The Proton AWG endpoint uses the first
proxy as its dialer detour; each proxy detours through the next one. UDP is
carried with XUDP so the AWG endpoint can traverse the proxy chain.

When proxy-chain mode is active, AWG junk, magic-header and special-packet
parameters are omitted from the generated configuration. Only parsed fields are
accepted; arbitrary sing-box JSON is not imported. Supported proxy transports
are TCP, WebSocket and HTTP Upgrade, with TLS and VLESS Reality/uTLS support.
Proxy hostnames are resolved before the AWG tunnel through a dedicated public
bootstrap DNS transport using sing-box's default platform-protected dialer.
The DNS transport must not detour through an empty `direct` outbound: sing-box
1.13 rejects that configuration. Android's `local` DNS transport is also not
used because the libbox process has no usable localhost DNS listener. Normal
application DNS still goes through Proton AWG.

Proxy share links are intentionally excluded from exported settings backups
because they contain authentication credentials.

## Security properties

- Private keys are never written to application logs.
- VPN server sockets are protected from routing back into the Android TUN.
- `strict_route` is enabled.
- Existing per-app and IP split-tunnel settings are mapped into TUN routes.
- The core AAR is pinned to one version and its source/license are documented.
- Only arm64 native code is shipped.

## Follow-up phases

The migration intentionally creates extension points for:

- domain/category route rules and remote rule sets;
- DNS routing/fallback policies;
- VLESS/Reality and VMess proxy-chain policies;
- chained detours before or after AWG;
- core-level connection and traffic telemetry through the command API;
- integration tests on a device/emulator with a controlled AWG endpoint.

These should be added as explicit policies rather than by concatenating arbitrary
user JSON into the trusted configuration.

## Connection readiness

The UI does not wait exclusively for Android's delayed
`NET_CAPABILITY_VALIDATED` captive-portal result. Each connection attempt
snapshots existing VPN network handles, waits for the new VPN network, and
marks it connected as soon as either:

- Android reports the new network as validated; or
- a short TCP probe bound explicitly to that VPN network succeeds.

Binding the probe socket to the new VPN `Network` prevents the underlying Wi-Fi
or cellular connection from producing a false positive. Failed probes are
retried briefly; an established tunnel is still kept on timeout.

## Runtime transport health

The foreground service also watches structured AWGBox transport errors after a
connection has been verified. Repeated DNS, VLESS/VMess, or AWG timeout/reset
errors within a short window indicate that the established path has become
unusable (for example, after a DPI policy change). Successful DNS exchanges and
AWG handshakes reset the failure streak. Two consecutive relevant failures
trigger an in-place engine restart with the same configuration, immediately
returning the UI to the connecting state. A cooldown prevents restart storms
while blocking persists.
