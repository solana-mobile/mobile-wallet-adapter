---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
---

Reject in-flight Nostr relay requests with `ERROR_SESSION_CLOSED` when the session ends (socket close or error, relay `CLOSED`, `scenario.close()`, or a wallet `SESSION_END`) instead of leaving them pending forever.
