---
'@solana-mobile/wallet-adapter-mobile': patch
---

- `connect()` and `autoConnect()` now wait for the wallet connection and reject with a `WalletConnectionError` when it fails. Previously they resolved before the connection finished, and a failure surfaced only as an unhandled rejection.
- `signTransaction`, `signAllTransactions` and `sendTransaction` no longer throw `Missing signature for public key` for an unsigned legacy transaction before the wallet opens. Legacy transactions are now serialized for the wallet without requiring signatures, since the wallet adds them; versioned transactions are unchanged.
