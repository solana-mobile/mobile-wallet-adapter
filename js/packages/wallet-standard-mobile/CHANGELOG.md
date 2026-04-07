# @solana-mobile/wallet-standard-mobile

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
