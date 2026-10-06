---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
---

Settle in-flight JSON-RPC requests when the transport session ends, instead of leaving the call hung forever. Previously the request promise was never resolved or rejected when the session went away.

This now covers two situations in all three scenarios (local, remote, and Nostr):

- The connection dropped — a clean or unclean WebSocket close, a socket error, or a Nostr relay `CLOSED` message. Outstanding requests are rejected with `ERROR_SESSION_CLOSED`.
- An encrypted message failed session-layer validation — an out-of-order sequence number or a payload that could not be decrypted. Per the spec the session is now closed (which rejects the outstanding requests) rather than discarding the error and leaving the request pending.

This matches the behavior of the native client.
