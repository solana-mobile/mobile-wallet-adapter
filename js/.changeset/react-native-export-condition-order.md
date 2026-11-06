---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
'@solana-mobile/mobile-wallet-adapter-protocol-kit': patch
'@solana-mobile/mobile-wallet-adapter-protocol-web3js': patch
'@solana-mobile/wallet-adapter-mobile': patch
'@solana-mobile/wallet-standard-mobile': patch
---

Fix React Native apps resolving the browser build when their Metro config asserts the `browser` condition on every platform, for example by adding it to `resolver.unstable_conditionNames` (a common workaround for axios). Metro picks the first condition in the order a package declares them, and the `exports` maps declared `browser` before `react-native`, so those apps loaded the browser build on Android and failed with "The mobile wallet adapter protocol must be used in a secure context". `react-native` now comes first. Browser bundlers, and Expo's web bundles, don't assert `react-native` and still resolve the browser build. A web bundle built with `@react-native/metro-config`, which asserts `react-native` on every platform, now resolves the React Native build instead.
