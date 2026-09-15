---
'@solana-mobile/mobile-wallet-adapter-protocol-kit': patch
---

Restore the `^7.0.0 || ^8.0.0` ranges on `@solana/transaction-messages` and `@solana/transactions`. #1659 unintentionally narrowed them to `^8.2.0`, which gave apps on kit 7 a duplicate kit 8 subtree again.
