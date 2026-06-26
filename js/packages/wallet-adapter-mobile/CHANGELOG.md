# @solana-mobile/wallet-adapter-mobile

## 2.2.10

### Patch Changes

- 0bfe9bf: Migrate the JS package build and typecheck toolchain to TypeScript 6, and update Solana kit dependencies for TypeScript 6 peer compatibility.
- Updated dependencies [0bfe9bf]
    - @solana-mobile/mobile-wallet-adapter-protocol@2.2.10
    - @solana-mobile/mobile-wallet-adapter-protocol-web3js@2.2.10
    - @solana-mobile/wallet-standard-mobile@0.5.4

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
