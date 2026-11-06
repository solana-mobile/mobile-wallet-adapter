---
'@solana-mobile/wallet-standard-mobile': patch
'@solana-mobile/wallet-adapter-mobile': patch
---

Send `signAndSendTransaction` options to the wallet the way the protocol defines them. `wallet-standard-mobile` spread the Wallet Standard options (`minContextSlot`, `commitment`, `skipPreflight`, `maxRetries`) in camelCase at the top level of the request, where wallets don't look for them, so they were ignored. They are now sent as the protocol's `options` object (`min_context_slot`, `commitment`, `skip_preflight`, `max_retries`), and no `options` object is sent when none are set. `preflightCommitment` has no protocol equivalent and is not sent. `wallet-adapter-mobile`'s `sendTransaction` now also forwards `minContextSlot`, which it dropped.
