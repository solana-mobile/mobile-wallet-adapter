---
'@solana-mobile/mobile-wallet-adapter-walletlib': patch
---

Stop advertising a CommonJS build that is never produced.

The `node` condition declared `"require": "./lib/cjs/index.js"`, but this package builds ESM only — `tsdown.config.ts` emits `format: 'esm'` into `lib/esm`, and `postbuild` writes only the `lib/esm` type marker. Nothing has produced `lib/cjs` since the build moved to tsdown, so the file was absent from the published tarball and `require('@solana-mobile/mobile-wallet-adapter-walletlib')` failed with `MODULE_NOT_FOUND` naming a path that had never existed.

The `node` condition now points at the ESM build that is actually shipped. Importers are unaffected; `require` callers on Node 22.12 and later load it through `require(esm)`, and on older versions get a plain "cannot require an ES module" error instead of a missing-file one.
