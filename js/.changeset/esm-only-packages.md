---
'@solana-mobile/mobile-wallet-adapter-protocol': major
'@solana-mobile/mobile-wallet-adapter-protocol-kit': minor
'@solana-mobile/mobile-wallet-adapter-protocol-web3js': major
'@solana-mobile/mobile-wallet-adapter-walletlib': patch
'@solana-mobile/wallet-adapter-mobile': major
'@solana-mobile/wallet-standard-mobile': minor
---

Ship ESM only and drop the CommonJS build.

- The `exports` map now resolves every condition to the ESM build under `lib/esm`. The `lib/cjs` directory is no longer published.
- `require()` callers need Node 20.19 or later on the 20.x line, or Node 22.12 and later, which load ES modules through `require()` without a flag. Node 21 and 22.0 to 22.11 are not supported. CommonJS consumers such as `@solana/wallet-adapter-react` keep working unchanged on supported versions. Each package declares this range in `engines.node`.
- React Native (Metro), Vite, webpack and Next.js consumers are unaffected. The `react-native` condition already resolved to a bundler-transpiled entry and now points at `lib/esm/index.native.js`.
- `@solana-mobile/mobile-wallet-adapter-walletlib` was already ESM only. Its `node` condition is now the `default` condition so every resolver, not only Node, can load it.
