---
'@solana-mobile/wallet-standard-mobile': patch
---

Map Wallet Standard sign-and-send options onto the protocol `options` object.

`signAndSendTransactions` was spreading camelCase Wallet Standard fields at the top level of the RPC params. Spec-compliant wallets look for `options.min_context_slot`, `options.skip_preflight`, `options.max_retries`, and `options.commitment`, so those values were ignored. Empty option objects are still omitted.
