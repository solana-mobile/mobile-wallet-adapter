---
'@solana-mobile/wallet-standard-mobile': patch
---

Ignore a wallet authorization that completes after `disconnect()` was called, instead of caching it and reconnecting. This covers the cache read, chain selection, the wallet's approval, a silent connect, and reauthorization during a signing request. The interrupted `connect()` now rejects with a cancellation error instead of staying pending, so wrappers such as wallet-adapter's `StandardWalletAdapter` can reset their connecting state. When a remote `disconnect()` runs while the QR code is waiting for the wallet, the session opened afterwards is closed.
