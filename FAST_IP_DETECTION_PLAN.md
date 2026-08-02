# Fast IP detection

- [x] Mirror the official Proton client flow by exposing the selected Proton physical server exit IP immediately after connection.
- [x] Use Proton's authenticated `vpn/v1/location` response as the primary confirmation/source for IP and country.
- [x] Keep third-party public-IP services only as a bounded fallback when Proton location fails.
- [x] Reset stale location state and avoid pre-VPN socket reuse after server switches.
- [x] Add parser/source-selection tests and update dashboard tests.
- [x] Run compile, unit tests, APK assembly, diff review, and commit only intended files.
