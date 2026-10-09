# @solana-mobile/mobile-wallet-adapter-protocol

## 3.0.1

## 3.0.0

### Major Changes

- da43b96: Ship ESM only and drop the CommonJS build.

    - The `exports` map now resolves every condition to the ESM build under `lib/esm`. The `lib/cjs` directory is no longer published.
    - `require()` callers need Node 20.19 or later on the 20.x line, or Node 22.12 and later, which load ES modules through `require()` without a flag. Node 21 and 22.0 to 22.11 are not supported. CommonJS consumers such as `@solana/wallet-adapter-react` keep working unchanged on supported versions. Each package declares this range in `engines.node`.
    - React Native (Metro), Vite, webpack and Next.js consumers are unaffected. The `react-native` condition already resolved to a bundler-transpiled entry and now points at `lib/esm/index.native.js`.
    - `@solana-mobile/mobile-wallet-adapter-walletlib` was already ESM only. Its `node` condition is now the `default` condition so every resolver, not only Node, can load it.

- 16a532e: Require `@solana/kit` 8.

    - `mobile-wallet-adapter-protocol-kit` declares `@solana/kit@^8.0.0` as its peer and depends on `@solana/transaction-messages` and `@solana/transactions` `^8.3.0`. The previous `^7.0.0 || ^8.0.0` peer was misleading: the dependencies already required Kit 8 internals, so Kit 7 apps got a duplicate Kit 8 subtree.
    - `mobile-wallet-adapter-protocol` depends on `@solana/kit@^8.3.0`.
    - Apps on Kit 7 must upgrade to `@solana/kit@^8.3.0`. The Kit changesets cover the migration.

### Minor Changes

- c258ef8: Declare `react-native` as an optional peer dependency.

    It was previously a required peer dependency, which npm 7 and later install automatically, so browser-only consumers were pulling the entire React Native toolchain into `node_modules` — roughly 32 MB of `react-native` itself, plus `metro` and the transitive advisories it carries — even though no browser code path imports it. React Native is reached only through the `react-native` export condition, so nothing about which code runs where has changed; only the manifest was wrong.

    React Native consumers are unaffected: they already depend on `react-native` directly, and an optional peer dependency resolves identically for them.

### Patch Changes

- 8835782: Settle in-flight JSON-RPC requests when the transport session ends, instead of leaving the call hung forever. Previously the request promise was never resolved or rejected when the session went away.

    This now covers two situations in all three scenarios (local, remote, and Nostr):

    - The connection dropped — a clean or unclean WebSocket close, a socket error, or a Nostr relay `CLOSED` message. Outstanding requests are rejected with `ERROR_SESSION_CLOSED`.
    - An encrypted message failed session-layer validation — an out-of-order sequence number or a payload that could not be decrypted. Per the spec the session is now closed (which rejects the outstanding requests) rather than discarding the error and leaving the request pending.

    This matches the behavior of the native client.

- e9f68c2: Support Solana v1 transactions.

    `wallet-standard-mobile`

    - The local wallet now advertises the `supportedTransactionVersions` the connected MWA wallet reports, instead of always `['legacy', 0]`. The remote wallet already did this.
    - A wallet that reports `1` is now advertised as supporting v1 transactions. A wallet that reports only `legacy` is no longer advertised as supporting v0.

    `mobile-wallet-adapter-protocol`

    - `supported_transaction_versions` is typed with `SolanaTransactionVersion` from `@solana/wallet-standard-features` instead of `TransactionVersion` from `@solana/web3.js`, so it includes `1`.
    - Requires `@solana/wallet-standard-features` `^1.5.0` (also bumped in `wallet-standard-mobile` and `wallet-adapter-mobile`).
    - Dropped the unused `@solana/web3.js` devDependency.

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
