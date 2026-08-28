---
'@solana-mobile/mobile-wallet-adapter-protocol': minor
---

Declare `react-native` as an optional peer dependency.

It was previously a required peer dependency, which npm 7 and later install automatically, so browser-only consumers were pulling the entire React Native toolchain into `node_modules` — roughly 32 MB of `react-native` itself, plus `metro` and the transitive advisories it carries — even though no browser code path imports it. React Native is reached only through the `react-native` export condition, so nothing about which code runs where has changed; only the manifest was wrong.

React Native consumers are unaffected: they already depend on `react-native` directly, and an optional peer dependency resolves identically for them.
