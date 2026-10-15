---
'@solana-mobile/wallet-standard-mobile': patch
---

Stop timing out `connect` while the user is still answering the Local Network Access permission dialogs, and keep the permission explainer open with a visible close button until the browser prompt is answered so a dismissed prompt can be retried or cancelled. Disconnecting during the permission flow now closes its dialogs and no longer launches the wallet.
