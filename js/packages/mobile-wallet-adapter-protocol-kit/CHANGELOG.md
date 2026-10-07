# @solana-mobile/mobile-wallet-adapter-protocol-kit

## 3.0.0

### Major Changes

- 16a532e: Require `@solana/kit` 8.

    - `mobile-wallet-adapter-protocol-kit` declares `@solana/kit@^8.0.0` as its peer and depends on `@solana/transaction-messages` and `@solana/transactions` `^8.3.0`. The previous `^7.0.0 || ^8.0.0` peer was misleading: the dependencies already required Kit 8 internals, so Kit 7 apps got a duplicate Kit 8 subtree.
    - `mobile-wallet-adapter-protocol` depends on `@solana/kit@^8.3.0`.
    - Apps on Kit 7 must upgrade to `@solana/kit@^8.3.0`. The Kit changesets cover the migration.

### Minor Changes

- 1de01bd: Align the package version with `@solana-mobile/mobile-wallet-adapter-protocol` and `@solana-mobile/mobile-wallet-adapter-protocol-web3js`. The three protocol packages are now released together as a fixed group, so from this release onward they always share the same version number. This is a versioning change only; there are no code or API changes in this package beyond those listed separately.
- da43b96: Ship ESM only and drop the CommonJS build.

    - The `exports` map now resolves every condition to the ESM build under `lib/esm`. The `lib/cjs` directory is no longer published.
    - `require()` callers need Node 20.19 or later on the 20.x line, or Node 22.12 and later, which load ES modules through `require()` without a flag. Node 21 and 22.0 to 22.11 are not supported. CommonJS consumers such as `@solana/wallet-adapter-react` keep working unchanged on supported versions. Each package declares this range in `engines.node`.
    - React Native (Metro), Vite, webpack and Next.js consumers are unaffected. The `react-native` condition already resolved to a bundler-transpiled entry and now points at `lib/esm/index.native.js`.
    - `@solana-mobile/mobile-wallet-adapter-walletlib` was already ESM only. Its `node` condition is now the `default` condition so every resolver, not only Node, can load it.

### Patch Changes

- Updated dependencies [da43b96]
- Updated dependencies [c258ef8]
- Updated dependencies [8835782]
- Updated dependencies [16a532e]
- Updated dependencies [e9f68c2]
    - @solana-mobile/mobile-wallet-adapter-protocol@3.0.0

## 0.4.0

### Minor Changes

- 0c55575: Bump the `@solana/kit` peer dependency from `^6.9.0` to `^7.0.0`.

### Patch Changes

- 0bfe9bf: Migrate the JS package build and typecheck toolchain to TypeScript 6, and update Solana kit dependencies for TypeScript 6 peer compatibility.
- Updated dependencies [0bfe9bf]
- Updated dependencies [25296e1]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.3.0

## 0.3.2

### Patch Changes

- c4ffb7a: Prepare the JS packages for a future TypeScript 6 upgrade without changing the current TypeScript version.
- c260601: Share protocol encoding helpers across JS mobile wallet packages.
- Updated dependencies [c4ffb7a]
- Updated dependencies [c260601]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.9

## 0.3.1

### Patch Changes

- a2e8d0d: Restore ESLint checks in the JS workspace and apply the package source updates needed for lint compliance.

    Add narrow `eslint-disable-next-line` comments in package source where platform requirements or existing runtime behavior conflict with the restored lint rules, while keeping package behavior unchanged.

- Updated dependencies [a2e8d0d]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.8

## 0.3.0

### Minor Changes

- 06dc333: Update the JS packages to the current Solana dependency ranges and refresh the workspace lockfile.

    Raise the protocol kit package to the current `@solana/kit` and transaction libraries, align the web3.js-based packages on `@solana/web3.js` `1.98.4`, and update the wallet-standard dependencies used by the mobile adapters.

### Patch Changes

- 7b35afb: Replace the Rollup-based JS package builds with tsdown while preserving the published CJS, ESM, and types output layout.

    Update the generated package metadata step so JS package builds complete cleanly on Node 24.

- 31fc3af: Add a JS workspace `check-types` task and wire it through the published package scripts.

    Update the protocol kit transaction typing used by `signAndSendTransactions`, remove the unused walletlib native module shim, and enable `skipLibCheck` for the workspace typecheck.

- Updated dependencies [7b35afb]
- Updated dependencies [31fc3af]
- Updated dependencies [06dc333]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.7

## 0.2.3

### Patch Changes

- 53a2139: Initialize Changeset a publish all and include all unreleased changes made since the last published version
- Updated dependencies [53a2139]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.6
