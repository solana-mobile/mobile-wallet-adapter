---
'@solana-mobile/wallet-standard-mobile': patch
---

`disconnect()` during the last step of `connect()`, while the authorization is being saved to the cache, now rejects that `connect()` with the cancellation error. Previously it resolved with no accounts, or never settled if the cache write didn't.
