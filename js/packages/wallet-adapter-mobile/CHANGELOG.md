# @solana-mobile/wallet-adapter-mobile

## 2.4.0

### Minor Changes

- c258ef8: Declare `react-native` and `@react-native-async-storage/async-storage` as optional peer dependencies, moving async-storage out of `optionalDependencies` and widening its accepted range to `^1.17.7 || ^2.0.0`, and load async-storage lazily.

    `react-native` was a required peer dependency and async-storage sat in `optionalDependencies`, both of which package managers install by default, so browser-only consumers were pulling the entire React Native toolchain — including `metro` and the transitive advisories it carries — into `node_modules`. Both packages are reached only through the `react-native` export condition, so which code runs where is unchanged; a browser-only install no longer contains `react-native`, `metro`, or async-storage.

    **Action required for React Native consumers that use the default authorization result cache.** If you call `createDefaultAuthorizationResultCache()`, add `@react-native-async-storage/async-storage` to your own app dependencies — it is no longer installed transitively. The release also adds runtime validation: async-storage is now resolved lazily, on first use, and `createDefaultAuthorizationResultCache()` throws a descriptive error when it is missing or unusable (such as when its native module has not been linked) instead of failing silently into an inert cache. Apps that pass their own `authorizationResultCache` never load async-storage and do not need it installed; the lazy `require` sits inside a `try` block, which Metro's default `allowOptionalDependencies` configuration treats as an optional dependency, so their builds keep working without it.

### Patch Changes

- Updated dependencies [f72388e]
- Updated dependencies [c258ef8]
- Updated dependencies [c258ef8]
- Updated dependencies [784619b]
    - @solana-mobile/wallet-standard-mobile@0.7.0
    - @solana-mobile/mobile-wallet-adapter-protocol@2.4.0
    - @solana-mobile/mobile-wallet-adapter-protocol-web3js@2.4.0

## 2.3.0

### Minor Changes

- 25296e1: Add support for a new local and remote MWA transport: Nostr Relays!

### Patch Changes

- 0bfe9bf: Migrate the JS package build and typecheck toolchain to TypeScript 6, and update Solana kit dependencies for TypeScript 6 peer compatibility.
- Updated dependencies [0bfe9bf]
- Updated dependencies [25296e1]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.3.0
    - @solana-mobile/mobile-wallet-adapter-protocol-web3js@2.3.0
    - @solana-mobile/wallet-standard-mobile@0.6.0

## 2.2.9

### Patch Changes

- c4ffb7a: Prepare the JS packages for a future TypeScript 6 upgrade without changing the current TypeScript version.
- c260601: Share protocol encoding helpers across JS mobile wallet packages.
- Updated dependencies [c4ffb7a]
- Updated dependencies [c260601]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.9
    - @solana-mobile/mobile-wallet-adapter-protocol-web3js@2.2.9
    - @solana-mobile/wallet-standard-mobile@0.5.3

## 2.2.8

### Patch Changes

- a2e8d0d: Restore ESLint checks in the JS workspace and apply the package source updates needed for lint compliance.

    Add narrow `eslint-disable-next-line` comments in package source where platform requirements or existing runtime behavior conflict with the restored lint rules, while keeping package behavior unchanged.

- Updated dependencies [a2e8d0d]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.8
    - @solana-mobile/mobile-wallet-adapter-protocol-web3js@2.2.8
    - @solana-mobile/wallet-standard-mobile@0.5.2

## 2.2.7

### Patch Changes

- 7b35afb: Replace the Rollup-based JS package builds with tsdown while preserving the published CJS, ESM, and types output layout.

    Update the generated package metadata step so JS package builds complete cleanly on Node 24.

- 31fc3af: Add a JS workspace `check-types` task and wire it through the published package scripts.

    Update the protocol kit transaction typing used by `signAndSendTransactions`, remove the unused walletlib native module shim, and enable `skipLibCheck` for the workspace typecheck.

- 06dc333: Update the JS packages to the current Solana dependency ranges and refresh the workspace lockfile.

    Raise the protocol kit package to the current `@solana/kit` and transaction libraries, align the web3.js-based packages on `@solana/web3.js` `1.98.4`, and update the wallet-standard dependencies used by the mobile adapters.

- Updated dependencies [a58cb20]
- Updated dependencies [7b35afb]
- Updated dependencies [31fc3af]
- Updated dependencies [06dc333]
    - @solana-mobile/wallet-standard-mobile@0.5.1
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.7
    - @solana-mobile/mobile-wallet-adapter-protocol-web3js@2.2.7

## 2.2.6

### Patch Changes

- 53a2139: Initialize Changeset a publish all and include all unreleased changes made since the last published version
- Updated dependencies [53a2139]
    - @solana-mobile/wallet-standard-mobile@0.5.0
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.6
    - @solana-mobile/mobile-wallet-adapter-protocol-web3js@2.2.6
