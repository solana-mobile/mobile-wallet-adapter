---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
'@solana-mobile/mobile-wallet-adapter-protocol-kit': patch
'@solana-mobile/mobile-wallet-adapter-protocol-web3js': patch
'@solana-mobile/wallet-adapter-mobile': patch
'@solana-mobile/wallet-standard-mobile': patch
---

Fix React Native apps resolving the browser build. The `exports` maps declared the `browser` condition before `react-native`, and Metro matches conditions in the order a package declares them while asserting both for a React Native bundle, so it picked the browser build and failed with a secure-context error (seen with Expo SDK 53 and React Native 0.79, which enable package exports by default). `react-native` now comes first. Browser bundlers, which don't assert `react-native`, still resolve the browser build.
