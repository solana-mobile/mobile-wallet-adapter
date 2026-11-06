---
'@solana-mobile/wallet-standard-mobile': patch
---

A `disconnect()` during `connect()` now wins: an authorization the wallet approves afterwards is no longer cached or applied, so the app stays disconnected, including after a reload. This covers the cache read, chain selection, the wallet's approval screen, a silent connect, and reauthorization during a signing request. The interrupted `connect()` rejects with a cancellation error instead of staying pending, so wrappers such as wallet-adapter's `StandardWalletAdapter` reset their connecting state. For the remote wallet, a session that the wallet opens after a `disconnect()` during the QR code wait is closed without sending it any request.
