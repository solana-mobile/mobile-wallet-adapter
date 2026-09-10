---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
'@solana-mobile/wallet-adapter-mobile': patch
'@solana-mobile/wallet-standard-mobile': patch
---

Support Solana v1 transactions.

`wallet-standard-mobile`

- The local wallet now advertises the `supportedTransactionVersions` the connected MWA wallet reports, instead of always `['legacy', 0]`. The remote wallet already did this.
- A wallet that reports `1` is now advertised as supporting v1 transactions. A wallet that reports only `legacy` is no longer advertised as supporting v0.

`mobile-wallet-adapter-protocol`

- `supported_transaction_versions` is typed with `SolanaTransactionVersion` from `@solana/wallet-standard-features` instead of `TransactionVersion` from `@solana/web3.js`, so it includes `1`.
- Requires `@solana/wallet-standard-features` `^1.5.0` (also bumped in `wallet-standard-mobile` and `wallet-adapter-mobile`).
- Dropped the unused `@solana/web3.js` devDependency.
