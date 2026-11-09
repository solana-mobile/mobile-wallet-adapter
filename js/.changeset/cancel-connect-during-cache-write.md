---
'@solana-mobile/wallet-standard-mobile': patch
---

`disconnect()` during the last step of `connect()`, while the authorization is being saved to the cache, now rejects that `connect()` with the cancellation error. Previously it resolved with no accounts, or never settled if the cache write didn't. A local authorization whose cache write finishes after `disconnect()` is no longer restored by a later `connect()`, and a `disconnect()` from a listener of the features change during `connect()` stops the authorization from being applied or cached. `cachedAuthorizationResult` no longer returns an authorization that `disconnect()` dropped, and a `disconnect()` also cancels a `connect()` still waiting for an earlier cache write.
