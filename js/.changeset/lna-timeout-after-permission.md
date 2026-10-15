---
'@solana-mobile/wallet-standard-mobile': patch
---

Stop timing out `connect` while the user is still answering the Local Network Access permission dialogs, keep the permission explainer open until the browser prompt is answered so a dismissed prompt can be retried or cancelled, and never authorize after the caller already received a timeout error.
