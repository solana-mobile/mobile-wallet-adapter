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

Only the key order changed — no target paths were added, removed or repointed. The only resolutions that move are React Native ones, which now reach the native build they were always meant to; browser, node, workerd and edge-light are unaffected.

`types` also moves to the front. TypeScript matches conditions in declaration order too, so with `types` last a consumer that also asserts a runtime condition could match a JavaScript entry before reaching `./lib/types/index.d.ts`. Declaring it first is what TypeScript documents, and it means the declaration file is selected ahead of any runtime condition.
