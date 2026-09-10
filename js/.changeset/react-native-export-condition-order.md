---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
'@solana-mobile/mobile-wallet-adapter-protocol-web3js': patch
'@solana-mobile/mobile-wallet-adapter-protocol-kit': patch
'@solana-mobile/mobile-wallet-adapter-walletlib': patch
'@solana-mobile/wallet-adapter-mobile': patch
'@solana-mobile/wallet-standard-mobile': patch
---

Declare the `react-native` export condition before `browser`, and `types` first.

Conditional exports are matched in the order the package declares them, not in the order the bundler lists its conditions. Metro asserts `browser` alongside `react-native` when bundling a native app, so with `browser` declared first a React Native app resolved to `index.browser.js` instead of `index.native.js`. That build keeps the `assertSecureContext()` call the native fork exists to avoid, so `transact()` threw `The mobile wallet adapter protocol must be used in a secure context (https)` on device.

This is why the failure appeared with Expo SDK 53 and React Native 0.79, which enabled package exports by default; before that Metro used the legacy top-level `react-native` field, which was already correct and still is.

Only the key order changed — no target paths were added, removed or repointed. Resolution is unchanged for browser, node, workerd, edge-light and TypeScript consumers; the only resolutions that move are React Native ones, which now reach the native build they were always meant to.
