---
'@solana-mobile/wallet-standard-mobile': patch
---

Ignore a wallet authorization that completes after `disconnect()` was called, instead of caching it and reconnecting
