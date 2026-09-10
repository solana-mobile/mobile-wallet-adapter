# @solana-mobile/mobile-wallet-adapter-protocol

## 2.4.0

### Minor Changes

- c258ef8: Declare `react-native` as an optional peer dependency.

    It was previously a required peer dependency, which npm 7 and later install automatically, so browser-only consumers were pulling the entire React Native toolchain into `node_modules` — roughly 32 MB of `react-native` itself, plus `metro` and the transitive advisories it carries — even though no browser code path imports it. React Native is reached only through the `react-native` export condition, so nothing about which code runs where has changed; only the manifest was wrong.

    React Native consumers are unaffected: they already depend on `react-native` directly, and an optional peer dependency resolves identically for them.

### Patch Changes

- 784619b: Accept `@solana/kit` v8 alongside v7.

    `mobile-wallet-adapter-protocol-kit` widens its `@solana/kit` peer dependency and its `@solana/transaction-messages` / `@solana/transactions` dependencies to `^7.0.0 || ^8.0.0`, and `mobile-wallet-adapter-protocol` widens its `@solana/kit` dependency the same way. Apps on kit 8 previously ended up with a duplicate kit 7 tree in `node_modules` (and a peer-dependency conflict on `@solana/kit`); with the widened ranges everything dedupes against the app's kit tree, whichever major it uses. Every kit API these packages touch is unchanged between v7 and v8, so no code changes were needed.

## 2.3.0

### Minor Changes

- 25296e1: Add support for a new local and remote MWA transport: Nostr Relays!

### Patch Changes

- 0bfe9bf: Migrate the JS package build and typecheck toolchain to TypeScript 6, and update Solana kit dependencies for TypeScript 6 peer compatibility.

## 2.2.9

### Patch Changes

- c4ffb7a: Prepare the JS packages for a future TypeScript 6 upgrade without changing the current TypeScript version.
- c260601: Share protocol encoding helpers across JS mobile wallet packages.

## 2.2.8

### Patch Changes

- a2e8d0d: Restore ESLint checks in the JS workspace and apply the package source updates needed for lint compliance.

    Add narrow `eslint-disable-next-line` comments in package source where platform requirements or existing runtime behavior conflict with the restored lint rules, while keeping package behavior unchanged.

## 2.2.7

### Patch Changes

- 7b35afb: Replace the Rollup-based JS package builds with tsdown while preserving the published CJS, ESM, and types output layout.

    Update the generated package metadata step so JS package builds complete cleanly on Node 24.

- 31fc3af: Add a JS workspace `check-types` task and wire it through the published package scripts.

    Update the protocol kit transaction typing used by `signAndSendTransactions`, remove the unused walletlib native module shim, and enable `skipLibCheck` for the workspace typecheck.

- 06dc333: Update the JS packages to the current Solana dependency ranges and refresh the workspace lockfile.

    Raise the protocol kit package to the current `@solana/kit` and transaction libraries, align the web3.js-based packages on `@solana/web3.js` `1.98.4`, and update the wallet-standard dependencies used by the mobile adapters.

## 2.2.6

### Patch Changes

- 53a2139: Initialize Changeset a publish all and include all unreleased changes made since the last published version
