---
'@solana-mobile/mobile-wallet-adapter-protocol': major
'@solana-mobile/mobile-wallet-adapter-protocol-kit': major
---

Require `@solana/kit` 8.

- `mobile-wallet-adapter-protocol-kit` declares `@solana/kit@^8.0.0` as its peer and depends on `@solana/transaction-messages` and `@solana/transactions` `^8.3.0`. The previous `^7.0.0 || ^8.0.0` peer was misleading: the dependencies already required Kit 8 internals, so Kit 7 apps got a duplicate Kit 8 subtree.
- `mobile-wallet-adapter-protocol` depends on `@solana/kit@^8.3.0`.
- Apps on Kit 7 must upgrade to `@solana/kit@^8.3.0`. The Kit changesets cover the migration.
