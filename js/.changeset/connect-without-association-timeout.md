---
'@solana-mobile/wallet-standard-mobile': patch
---

Stop `connect` from failing with "Wallet connection timed out" while the user is still answering the Local Network Access permission dialogs. The permission explainer now stays open until the browser's prompt is answered, so a dismissed prompt can be retried. Calling `disconnect()` during the permission flow closes its dialogs, does not launch the wallet, and rejects the pending `connect()`.

A local association made over a Nostr relay now times out like one over localhost: if the wallet hasn't joined within 30 s of launching, the session is closed and the request fails with `ERROR_SESSION_TIMEOUT`, instead of waiting forever.
