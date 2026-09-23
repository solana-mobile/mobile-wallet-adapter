---
'@solana-mobile/wallet-standard-mobile': patch
---

Match the signed-in account by public key when `signIn` returns.

`authorize` accounts are stored as Wallet Standard accounts, so `address` is base58. `sign_in_result.address` is still the base64 address from the Mobile Wallet Adapter response. Comparing those strings never hit, and the sign-in result dropped the account label and icon and copied capability feature ids onto the account. The lookup now compares the base58 form of `sign_in_result.address`.
