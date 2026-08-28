---
'@solana-mobile/wallet-standard-mobile': patch
---

Fix connection deadlock on loopback-origin dapps (localhost dev via `adb reverse`). Local Network Access only gates requests that cross into a more private address space, so a page served from `localhost`, `*.localhost`, `127.0.0.0/8`, or `[::1]` fetching loopback is exempt — the browser never shows the permission prompt and the permission state can never leave "prompt", which left the connection hanging until the association timeout. Loopback origins now skip the permission gate entirely and go straight to the association intent; non-loopback origins keep the existing prompt flow.
