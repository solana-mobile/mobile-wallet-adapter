---
'@solana-mobile/wallet-standard-mobile': patch
---

Stop `connect` from failing with "Wallet connection timed out" while the user is still answering the Local Network Access permission dialogs. The permission explainer now stays open, with a close button, until the browser's prompt is answered, so a dismissed prompt can be retried or the flow cancelled; "Ready to connect!" also shows a close button. Calling `disconnect()` during the permission flow closes its dialogs, does not launch the wallet, and rejects the pending `connect()`.
