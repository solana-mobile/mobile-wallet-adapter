---
'@solana-mobile/wallet-adapter-mobile': patch
---

- `connect()` and `autoConnect()` now wait for the wallet connection. They reject with `WalletNotReadyError` when the adapter is not ready (for example, on an unsupported platform) and with `WalletConnectionError` when the wallet connection fails. Previously they resolved before the connection finished, and these errors surfaced only as unhandled rejections.
- `signTransaction`, `signAllTransactions` and `sendTransaction` no longer throw `Missing signature for public key` for an unsigned legacy transaction before the wallet opens. Legacy transactions are now serialized for the wallet without requiring signatures, since the wallet adds them; versioned transactions are unchanged.
