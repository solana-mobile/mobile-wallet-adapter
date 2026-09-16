---
'@solana-mobile/wallet-adapter-mobile': patch
---

Return a base58 transaction signature from `sendTransaction`.

Wallet Standard `signAndSendTransaction` yields raw signature bytes. Encoding those with base64 produced a string that RPC, explorers, and `connection.sendRawTransaction` do not accept. The protocol-web3js wrapper already converts the same bytes to base58.
