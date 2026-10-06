---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
---

Reject in-flight JSON-RPC requests when the transport session is torn down. Previously, if the connection closed while a request was awaiting its response — a clean or unclean WebSocket close, or a Nostr relay `CLOSED` message — the request promise was never settled and the call hung indefinitely. Each scenario (local, remote, and Nostr) now rejects any outstanding requests with `ERROR_SESSION_CLOSED` on teardown, matching the behavior of the native client.
