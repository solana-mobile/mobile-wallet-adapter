---
'@solana-mobile/wallet-standard-mobile': patch
---

The embedded modals (loading spinner, local connection, Local Network Access permission and blocked, remote connection QR code, and wallet not found) are now Lit custom elements. They look the same as before. Each modal is removed from the page when it closes instead of staying hidden in `document.body`, error messages render as text instead of HTML, and the dialogs have working accessible names. Their shadow roots are open, so page scripts and end-to-end tests can reach the dialog buttons. The React Native build does not import Lit. Adds `lit` as a dependency.
