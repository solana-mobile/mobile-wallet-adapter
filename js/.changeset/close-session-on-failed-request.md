---
'@solana-mobile/wallet-standard-mobile': patch
---

Close the wallet session when a request fails, instead of leaving the wallet open and waiting. Previously a declined or failed request skipped the session close, so the user was stuck in the wallet.

- Local: the session now closes after every request, whether it succeeded, failed, or was declined.
- Remote: the session closes when it can't move forward: the connection failed, or the dapp still isn't authorized (e.g. the user declined authorization). A declined signing request while authorized keeps the session open, as before. Starting a new authorization now closes any previous session instead of dropping it.
- Remote: when a request finds that the connection was lost while authorized, the authorization is cleared along with the session, so the next connect starts a fresh session instead of returning cached accounts with no session. A connection that drops while no request is running is only noticed by the next request. Errors from the adapter's handling of a malformed signing response keep the session and authorization. Errors while handling a reauthorization response can clear both. A connection attempt that fails before the wallet connects now closes its scenario.
