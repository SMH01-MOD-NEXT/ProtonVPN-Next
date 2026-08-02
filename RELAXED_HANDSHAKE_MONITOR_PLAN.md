# Relaxed handshake monitor refinement

- [x] Add a persisted configurable handshake timeout in seconds, defaulting to 5.
- [x] In handshake-only mode hide all behavior toggles and show only the reconnect timeout control.
- [x] Pass timeout changes to the running VPN service without requiring a reconnect.
- [x] Detect outgoing AWG handshake attempts after an established connection and enter a timed handshake verification window.
- [x] Treat normal obfuscation re-handshakes the same way: wait for the configured timer, accept a response, reconnect only on timeout.
- [x] Keep DNS probes and transport-error counters completely out of handshake-only mode.
- [x] Add focused tests, run the full unit suite/build, review, and commit.
