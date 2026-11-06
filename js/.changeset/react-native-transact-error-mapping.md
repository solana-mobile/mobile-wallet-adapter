---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
---

React Native: map a wallet request's rejection to `SolanaMobileWalletAdapterProtocolError` or `SolanaMobileWalletAdapterError` inside the `transact` callback, where it happens. Previously the native module's rejection skipped the mapping and the callback saw the raw React Native error, so code that branches on the protocol error (for example, falling back to `authorize` when `reauthorize` fails with `ERROR_AUTHORIZATION_FAILED`) never matched. An already-mapped error that the callback lets propagate now rejects `transact` unchanged instead of being wrapped again.
