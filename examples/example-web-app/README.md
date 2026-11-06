# Example Mobile Web dApp

A small web app that uses Mobile Wallet Adapter through [Wallet Standard](https://github.com/wallet-standard/wallet-standard), with no wallet-adapter or Solana SDK dependency. It is built with Vite and React and published to GitHub Pages.

## Features

- Connect, or sign in with Solana (the app verifies the sign-in message and signature).
- Record a message on devnet with the Memo program (sign and send).
- Sign the same memo transaction without sending it.
- Sign a message off-chain.
- Fund the account from the devnet faucet and show its balance.

`src/main.tsx` registers MWA with `registerMwa`. `src/App.tsx` lists every Solana wallet in the Wallet Standard registry, Mobile Wallet Adapter first, and calls the chosen wallet's features directly. Actions a wallet does not support are disabled. Each outcome shows in a notification and in the activity log.

On Android Chrome the app connects to a wallet on the same device. On a desktop browser it shows a QR code for a wallet to scan, which needs a relay: set `VITE_NOSTR_RELAY` (Nostr relay domain) or `VITE_REFLECTOR_HOST_AUTHORITY` (reflector host) at build time. The `?relay=`, `?reflector=` and `?rpc=` URL parameters override them for a single page load.

## Quick start

1. Install and run the dev server: `pnpm install && pnpm dev`. Installing also builds the SDK packages in `js/`, which the app links to.
2. To load it on a phone, open an https tunnel to port 3002 in another terminal with `pnpm tunnel:start` ([ngrok](https://ngrok.com/docs/getting-started)).
3. Open the https tunnel URL on an Android device that has an MWA wallet installed.

## Scripts

| Script              | What it does                                       |
| ------------------- | -------------------------------------------------- |
| `pnpm dev`          | Vite dev server on port 3002                       |
| `pnpm build`        | Type-checks and builds the static site into `out/` |
| `pnpm test`         | Unit tests (Vitest, happy-dom)                     |
| `pnpm e2e`          | Browser end-to-end test, described below           |
| `pnpm lint:check`   | ESLint                                             |
| `pnpm format:check` | Prettier                                           |
| `pnpm tunnel:start` | ngrok tunnel to the dev server                     |

## End-to-end test

`pnpm e2e` runs the real app in Chrome, driven by [agent-browser](https://www.npmjs.com/package/agent-browser), against a fake wallet that speaks MWA over Nostr. Everything runs locally: the test starts its own Nostr relay (over wss, with a throwaway self-signed certificate) and a Solana JSON-RPC stub, so it needs no network access or secrets. It covers connect, funding, signing a message, signing a transaction, sign and send, disconnect and sign-in, and verifies every signature against the fake wallet's key.

Before the first run, install agent-browser's Chrome with `pnpm exec agent-browser install`. The test also needs `openssl` on the `PATH`.
