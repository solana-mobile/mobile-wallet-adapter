# @solana-mobile/wallet-standard-mobile

## 0.7.0

### Minor Changes

- c258ef8: Move `@react-native-async-storage/async-storage` from `optionalDependencies` to an optional peer dependency, widen the accepted range to `^1.17.7 || ^2.0.0`, and load it lazily.

    `optionalDependencies` are installed by default by every package manager — "optional" means only that a failed install is tolerated, not that the package is skipped — so browser-only consumers were pulling async-storage and, through its own peer dependency on `react-native`, the entire React Native toolchain into `node_modules`. Together with the matching fix in `@solana-mobile/mobile-wallet-adapter-protocol`, a browser-only install of this package drops from about 207 MB to about 42 MB and from 334 packages to 93, and the seven high-severity `metro` advisories it used to surface in `npm audit` go away entirely. Downstream lockfiles will shrink substantially on the next update; that is this change taking effect, not packages going missing.

    **Action required for React Native consumers that use the default authorization cache.** If you call `createDefaultAuthorizationCache()`, add `@react-native-async-storage/async-storage` to your own app dependencies — it is no longer installed transitively. The release also adds runtime validation: async-storage is now resolved lazily, on first use, and `createDefaultAuthorizationCache()` throws a descriptive error when it is missing or unusable (such as when its native module has not been linked) instead of failing silently into an inert cache. Apps that pass their own `authorizationCache` never load async-storage and do not need it installed; the lazy `require` sits inside a `try` block, which Metro's default `allowOptionalDependencies` configuration treats as an optional dependency, so their builds keep working without it.

### Patch Changes

- f72388e: Fix connection deadlock on loopback-origin dapps (localhost dev via `adb reverse`). Local Network Access only gates requests that cross into a more private address space, so a page served from `localhost`, `*.localhost`, `127.0.0.0/8`, or `[::1]` fetching loopback is exempt — the browser never shows the permission prompt and the permission state can never leave "prompt", which left the connection hanging until the association timeout. Loopback origins now skip the permission gate entirely and go straight to the association intent; non-loopback origins keep the existing prompt flow.
- Updated dependencies [c258ef8]
- Updated dependencies [784619b]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.4.0

## 0.6.0

### Minor Changes

- 25296e1: Add support for a new local and remote MWA transport: Nostr Relays!

### Patch Changes

- 0bfe9bf: Migrate the JS package build and typecheck toolchain to TypeScript 6, and update Solana kit dependencies for TypeScript 6 peer compatibility.
- Updated dependencies [0bfe9bf]
- Updated dependencies [25296e1]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.3.0

## 0.5.3

### Patch Changes

- c4ffb7a: Prepare the JS packages for a future TypeScript 6 upgrade without changing the current TypeScript version.
- c260601: Share protocol encoding helpers across JS mobile wallet packages.
- Updated dependencies [c4ffb7a]
- Updated dependencies [c260601]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.9

## 0.5.2

### Patch Changes

- a2e8d0d: Restore ESLint checks in the JS workspace and apply the package source updates needed for lint compliance.

    Add narrow `eslint-disable-next-line` comments in package source where platform requirements or existing runtime behavior conflict with the restored lint rules, while keeping package behavior unchanged.

- Updated dependencies [a2e8d0d]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.8

## 0.5.1

### Patch Changes

- a58cb20: raise dialog z-index to max value (2147483647) to force MWA dialogs to draw above other UI.
- 7b35afb: Replace the Rollup-based JS package builds with tsdown while preserving the published CJS, ESM, and types output layout.

    Update the generated package metadata step so JS package builds complete cleanly on Node 24.

- 31fc3af: Add a JS workspace `check-types` task and wire it through the published package scripts.

    Update the protocol kit transaction typing used by `signAndSendTransactions`, remove the unused walletlib native module shim, and enable `skipLibCheck` for the workspace typecheck.

- 06dc333: Update the JS packages to the current Solana dependency ranges and refresh the workspace lockfile.

    Raise the protocol kit package to the current `@solana/kit` and transaction libraries, align the web3.js-based packages on `@solana/web3.js` `1.98.4`, and update the wallet-standard dependencies used by the mobile adapters.

- Updated dependencies [7b35afb]
- Updated dependencies [31fc3af]
- Updated dependencies [06dc333]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.7

## 0.5.0

### Minor Changes

- 53a2139: Initialize Changeset a publish all and include all unreleased changes made since the last published version

### Patch Changes

- Updated dependencies [53a2139]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.6
