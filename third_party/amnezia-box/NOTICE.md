# amnezia-box binary notice

The generated (git-ignored) `app/libs/libbox-awgbox.aar` is built from
[hoaxisr/amnezia-box](https://github.com/hoaxisr/amnezia-box), release
`1.14.0-rc.1-awgm.14`, with a minimal mobile feature set: `with_awg`, `with_utls`,
`with_clash_api`, `badlinkname` and `tfogo_checklinkname0`.

VLESS, VMess, SOCKS/HTTP and proxy chaining are included in the base sing-box
build. Optional QUIC (Hysteria2/TUIC), gVisor, standard WireGuard, Tailscale,
and Naive outbound components are excluded. Clash API remains internal-only because libbox CommandServer requires its connection tracker. The TUN uses Android's
system stack instead of gVisor.

The AAR is restricted to `android/arm64`, matching ProtonVPN-Next's supported ABI.
It contains sing-box, amneziawg-go and generated gomobile bindings. See `LICENSE`
and the upstream dependency licenses. The reproducible build entry point is
`scripts/build-awgbox-lib.sh`. Gradle sync invokes this script automatically; an
existing AAR is accepted only when it matches the committed SHA-256 checksum.
Woodpecker installs the pinned Go toolchain and runs the same script before the
Android build.
