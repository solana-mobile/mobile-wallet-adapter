---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
'@solana-mobile/mobile-wallet-adapter-protocol-kit': patch
---

Accept `@solana/kit` v8 alongside v7.

`mobile-wallet-adapter-protocol-kit` widens its `@solana/kit` peer dependency and its `@solana/transaction-messages` / `@solana/transactions` dependencies to `^7.0.0 || ^8.0.0`, and `mobile-wallet-adapter-protocol` widens its `@solana/kit` dependency the same way. Apps on kit 8 previously ended up with a duplicate kit 7 tree in `node_modules` (and a peer-dependency conflict on `@solana/kit`); with the widened ranges everything dedupes against the app's kit tree, whichever major it uses. Every kit API these packages touch is unchanged between v7 and v8, so no code changes were needed.
