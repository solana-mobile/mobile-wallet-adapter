---
'@solana-mobile/wallet-adapter-mobile': patch
---

Forward `minContextSlot` from `sendTransaction` onto Wallet Standard `signAndSendTransaction` options.

`skipPreflight` and `maxRetries` were already copied when any `SendOptions` object was present, but `minContextSlot` was dropped, so wallets never received the slot the dapp evaluated the transaction at. Defined fields are copied as camelCase Wallet Standard options; omitted fields are left off the object.
