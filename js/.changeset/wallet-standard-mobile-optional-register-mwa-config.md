---
'@solana-mobile/wallet-standard-mobile': minor
---

Make `authorizationCache`, `chainSelector` and `onWalletNotFound` optional in `registerMwa` and the wallet constructors, defaulting to `createDefaultAuthorizationCache()`, `createDefaultChainSelector()` and `createDefaultWalletNotFoundHandler()` respectively. The config shapes are now exported as `SolanaMobileWalletAdapterWalletConfig`, with `LocalSolanaMobileWalletAdapterWalletConfig`, `RemoteSolanaMobileWalletAdapterWalletConfig` and `NostrSolanaMobileWalletAdapterWalletConfig` extending it. Existing callers that pass all properties are unaffected.
