---
'@solana-mobile/mobile-wallet-adapter-protocol': patch
'@solana-mobile/mobile-wallet-adapter-protocol-kit': patch
'@solana-mobile/mobile-wallet-adapter-protocol-web3js': patch
'@solana-mobile/mobile-wallet-adapter-walletlib': patch
'@solana-mobile/wallet-adapter-mobile': patch
'@solana-mobile/wallet-standard-mobile': patch
---

Restore ESLint checks in the JS workspace and apply the package source updates needed for lint compliance.

Add narrow `eslint-disable-next-line` comments in package source where platform requirements or existing runtime behavior conflict with the restored lint rules, while keeping package behavior unchanged.
