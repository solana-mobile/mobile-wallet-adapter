---
'@solana-mobile/mobile-wallet-adapter-protocol-kit': patch
'@solana-mobile/mobile-wallet-adapter-protocol-web3js': patch
---

The React Native build no longer imports `startRemoteScenario` from `@solana-mobile/mobile-wallet-adapter-protocol`, whose React Native build only exports `transact`. Loading the native entry under a strict ESM loader such as vitest or Node used to throw `The requested module '@solana-mobile/mobile-wallet-adapter-protocol' does not provide an export named 'startRemoteScenario'`. `startRemoteScenario` is still exported on web and Node.
