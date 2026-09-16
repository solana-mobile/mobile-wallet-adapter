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
- `require()` callers need Node 20.19 or 22.12 and later, which load ES modules through `require()` natively. CommonJS consumers such as `@solana/wallet-adapter-react` keep working unchanged on those versions. Each package declares this floor in `engines.node`.
- Jest users must let Jest transform these packages, since Jest's own module loader cannot `require()` an ES module. Add `@solana-mobile` to `transformIgnorePatterns`, for example `'/node_modules/(?!@solana-mobile)'`.
- React Native (Metro), Vite, webpack and Next.js consumers are unaffected. The `react-native` condition already resolved to a bundler-transpiled entry and now points at `lib/esm/index.native.js`.
- `@solana-mobile/mobile-wallet-adapter-walletlib` was already ESM only. Its `node` condition is now the `default` condition so every resolver, not only Node, can load it.
