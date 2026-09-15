---
'@solana-mobile/wallet-adapter-mobile': patch
---

Serialize unsigned legacy transactions without requiring signatures first.

`Transaction.serialize()` defaults to `requireAllSignatures: true`, so `signTransaction`, `signAllTransactions`, and `sendTransaction` threw `Missing signature for public key` before the wallet prompt. Versioned transactions are unchanged. The protocol-web3js wrapper already serializes legacy transactions this way.
