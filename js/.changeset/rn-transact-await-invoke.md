---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
---

Fix the React Native `transact` fork so that rejected `invoke` calls are mapped to `SolanaMobileWalletAdapterProtocolError` / `SolanaMobileWalletAdapterError` inside the `transact` callback. `invoke` returns a promise and was returned without `await`, so its rejection bypassed the surrounding `catch`/`handleError` and reached the callback as the raw React Native error (`code: 'JSON_RPC_ERROR'`, `userInfo.jsonRpcErrorCode`). Callers that branch on the mapped error inside the callback (for example a `reauthorize` failure that should fall back to a fresh `authorize`) never matched.
