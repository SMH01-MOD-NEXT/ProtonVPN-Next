# Exclude Split Tunneling Fix Plan

1. [x] Trace app and route filtering from settings to Android `VpnService.Builder`.
2. [x] Move app filtering to an explicit service-to-platform policy instead of inferred libbox package iterators.
3. [x] Preserve Include semantics while making empty Exclude mean full-tunnel VPN.
4. [x] Add regression tests for disabled, Include, empty Exclude, and populated Exclude policies.
5. [x] Run unit tests and assemble Stable Standard Debug.
6. [x] Review status and commit only the intended fix files.
