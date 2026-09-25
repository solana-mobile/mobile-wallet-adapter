# @solana-mobile/mobile-wallet-adapter-walletlib

## 1.4.6

### Patch Changes

- 35fc89f: Stop advertising a CommonJS build that is never produced.

    The `node` condition declared `"require": "./lib/cjs/index.js"`, but this package builds ESM only — `tsdown.config.ts` emits `format: 'esm'` into `lib/esm`, and `postbuild` writes only the `lib/esm` type marker. Nothing has produced `lib/cjs` since the build moved to tsdown, so the file was absent from the published tarball and `require('@solana-mobile/mobile-wallet-adapter-walletlib')` failed with `MODULE_NOT_FOUND` naming a path that had never existed.

    The `node` condition now points at the ESM build that is actually shipped. Importers are unaffected; `require` callers on Node 22.12 and later load it through `require(esm)`, and on older versions get a plain "cannot require an ES module" error instead of a missing-file one.

## 1.4.5

### Patch Changes

- 0bfe9bf: Migrate the JS package build and typecheck toolchain to TypeScript 6, and update Solana kit dependencies for TypeScript 6 peer compatibility.

## 1.4.4

### Patch Changes

- c4ffb7a: Prepare the JS packages for a future TypeScript 6 upgrade without changing the current TypeScript version.
- 62dbb48: Point the React Native package entrypoint at the native ESM build and include source files required by React Native codegen.

## 1.4.3

### Patch Changes

- a2e8d0d: Restore ESLint checks in the JS workspace and apply the package source updates needed for lint compliance.

    Add narrow `eslint-disable-next-line` comments in package source where platform requirements or existing runtime behavior conflict with the restored lint rules, while keeping package behavior unchanged.

## 1.4.2

### Patch Changes

- 7b35afb: Replace the Rollup-based JS package builds with tsdown while preserving the published CJS, ESM, and types output layout.

    Update the generated package metadata step so JS package builds complete cleanly on Node 24.

- 31fc3af: Add a JS workspace `check-types` task and wire it through the published package scripts.

    Update the protocol kit transaction typing used by `signAndSendTransactions`, remove the unused walletlib native module shim, and enable `skipLibCheck` for the workspace typecheck.

## 1.4.1

### Patch Changes

- 53a2139: Initialize Changeset a publish all and include all unreleased changes made since the last published version
