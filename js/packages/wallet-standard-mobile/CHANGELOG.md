# @solana-mobile/wallet-standard-mobile

## 0.7.1

### Patch Changes

- e336b15: Close the wallet session when a request fails, instead of leaving the wallet open and waiting. Previously a declined or failed request skipped the session close, so the user was stuck in the wallet.

    - Local: the session now closes after every request, whether it succeeded, failed, or was declined.
    - Remote: the session closes when it can't move forward: the connection failed, or the dapp still isn't authorized (e.g. the user declined authorization). A declined signing request while authorized keeps the session open, as before. Starting a new authorization now closes any previous session instead of dropping it.
    - Remote: when a request finds that the connection was lost while authorized, the authorization is cleared along with the session, so the next connect starts a fresh session instead of returning cached accounts with no session. A connection that drops while no request is running is only noticed by the next request. Errors from the adapter's handling of a malformed signing response keep the session and authorization. Errors while handling a reauthorization response can clear both. A connection attempt that fails before the wallet connects now closes its scenario.

- e336b15: Stop `connect` from failing with "Wallet connection timed out" while the user is still answering the Local Network Access permission dialogs. The permission explainer now stays open until the browser's prompt is answered, so a dismissed prompt can be retried. Calling `disconnect()` during the permission flow closes its dialogs, does not launch the wallet, and rejects the pending `connect()`.

    A local association made over a Nostr relay now times out like one over localhost: if the wallet hasn't joined within 30 s of launching, the session is closed and the request fails with `ERROR_SESSION_TIMEOUT`, instead of waiting forever.

- e336b15: A `disconnect()` during `connect()` now wins: an authorization the wallet approves afterwards is no longer cached or applied, so the app stays disconnected, including after a reload. This covers the cache read, chain selection, the wallet's approval screen, a silent connect, and reauthorization during a signing request. The interrupted `connect()` rejects with a cancellation error instead of staying pending, so wrappers such as wallet-adapter's `StandardWalletAdapter` reset their connecting state. For the remote wallet, a session that the wallet opens after a `disconnect()` during the QR code wait is closed without sending it any request.
- 01fd175: The embedded modals (loading spinner, local connection, Local Network Access permission and blocked, remote connection QR code, and wallet not found) are now Lit custom elements. They look the same as before. Each modal is removed from the page when it closes instead of staying hidden in `document.body`, error messages render as text instead of HTML, and the dialogs have working accessible names. Their shadow roots are open, so page scripts and end-to-end tests can reach the dialog buttons. The React Native build does not import Lit. Adds `lit` as a dependency.
- 2bceaf0: Fix React Native apps resolving the browser build when their Metro config asserts the `browser` condition on every platform, for example by adding it to `resolver.unstable_conditionNames` (a common workaround for axios). Metro picks the first condition in the order a package declares them, and the `exports` maps declared `browser` before `react-native`, so those apps loaded the browser build on Android and failed with "The mobile wallet adapter protocol must be used in a secure context". `react-native` now comes first. Browser bundlers, and Expo's web bundles, don't assert `react-native` and still resolve the browser build. A web bundle built with `@react-native/metro-config`, which asserts `react-native` on every platform, now resolves the React Native build instead.
- 202a6a3: Send `signAndSendTransaction` options to the wallet the way the protocol defines them. `wallet-standard-mobile` spread the Wallet Standard options (`minContextSlot`, `commitment`, `skipPreflight`, `maxRetries`) in camelCase at the top level of the request, where wallets don't look for them, so they were ignored. They are now sent as the protocol's `options` object (`min_context_slot`, `commitment`, `skip_preflight`, `max_retries`), and no `options` object is sent when none are set. `preflightCommitment` has no protocol equivalent and is not sent. `wallet-adapter-mobile`'s `sendTransaction` now also forwards `minContextSlot`, which it dropped.
- Updated dependencies [2bceaf0]
- Updated dependencies [cde28e3]
    - @solana-mobile/mobile-wallet-adapter-protocol@3.0.2

## 0.7.0

### Minor Changes

- da43b96: Ship ESM only and drop the CommonJS build.

    - The `exports` map now resolves every condition to the ESM build under `lib/esm`. The `lib/cjs` directory is no longer published.
    - `require()` callers need Node 20.19 or later on the 20.x line, or Node 22.12 and later, which load ES modules through `require()` without a flag. Node 21 and 22.0 to 22.11 are not supported. CommonJS consumers such as `@solana/wallet-adapter-react` keep working unchanged on supported versions. Each package declares this range in `engines.node`.
    - React Native (Metro), Vite, webpack and Next.js consumers are unaffected. The `react-native` condition already resolved to a bundler-transpiled entry and now points at `lib/esm/index.native.js`.
    - `@solana-mobile/mobile-wallet-adapter-walletlib` was already ESM only. Its `node` condition is now the `default` condition so every resolver, not only Node, can load it.

- c258ef8: Move `@react-native-async-storage/async-storage` from `optionalDependencies` to an optional peer dependency, widen the accepted range to `^1.17.7 || ^2.0.0`, and load it lazily.

    `optionalDependencies` are installed by default by every package manager — "optional" means only that a failed install is tolerated, not that the package is skipped — so browser-only consumers were pulling async-storage and, through its own peer dependency on `react-native`, the entire React Native toolchain into `node_modules`. Together with the matching fix in `@solana-mobile/mobile-wallet-adapter-protocol`, a browser-only install of this package drops from about 207 MB to about 42 MB and from 334 packages to 93, and the seven high-severity `metro` advisories it used to surface in `npm audit` go away entirely. Downstream lockfiles will shrink substantially on the next update; that is this change taking effect, not packages going missing.

    **Action required for React Native consumers that use the default authorization cache.** If you call `createDefaultAuthorizationCache()`, add `@react-native-async-storage/async-storage` to your own app dependencies — it is no longer installed transitively. The release also adds runtime validation: async-storage is now resolved lazily, on first use, and `createDefaultAuthorizationCache()` throws a descriptive error when it is missing or unusable (such as when its native module has not been linked) instead of failing silently into an inert cache. Apps that pass their own `authorizationCache` never load async-storage and do not need it installed; the lazy `require` sits inside a `try` block, which Metro's default `allowOptionalDependencies` configuration treats as an optional dependency, so their builds keep working without it.

- 50ccaca: Make `authorizationCache`, `chainSelector` and `onWalletNotFound` optional in `registerMwa` and the wallet constructors, defaulting to `createDefaultAuthorizationCache()`, `createDefaultChainSelector()` and `createDefaultWalletNotFoundHandler()` respectively. The config shapes are now exported as `SolanaMobileWalletAdapterWalletConfig`, with `LocalSolanaMobileWalletAdapterWalletConfig`, `RemoteSolanaMobileWalletAdapterWalletConfig` and `NostrSolanaMobileWalletAdapterWalletConfig` extending it. Existing callers that pass all properties are unaffected.

### Patch Changes

- f72388e: Fix connection deadlock on loopback-origin dapps (localhost dev via `adb reverse`). Local Network Access only gates requests that cross into a more private address space, so a page served from `localhost`, `*.localhost`, `127.0.0.0/8`, or `[::1]` fetching loopback is exempt — the browser never shows the permission prompt and the permission state can never leave "prompt", which left the connection hanging until the association timeout. Loopback origins now skip the permission gate entirely and go straight to the association intent; non-loopback origins keep the existing prompt flow.
- e9f68c2: Support Solana v1 transactions.

    `wallet-standard-mobile`

    - The local wallet now advertises the `supportedTransactionVersions` the connected MWA wallet reports, instead of always `['legacy', 0]`. The remote wallet already did this.
    - A wallet that reports `1` is now advertised as supporting v1 transactions. A wallet that reports only `legacy` is no longer advertised as supporting v0.

    `mobile-wallet-adapter-protocol`

    - `supported_transaction_versions` is typed with `SolanaTransactionVersion` from `@solana/wallet-standard-features` instead of `TransactionVersion` from `@solana/web3.js`, so it includes `1`.
    - Requires `@solana/wallet-standard-features` `^1.5.0` (also bumped in `wallet-standard-mobile` and `wallet-adapter-mobile`).
    - Dropped the unused `@solana/web3.js` devDependency.

- Updated dependencies [da43b96]
- Updated dependencies [c258ef8]
- Updated dependencies [8835782]
- Updated dependencies [16a532e]
- Updated dependencies [e9f68c2]
    - @solana-mobile/mobile-wallet-adapter-protocol@3.0.0

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
