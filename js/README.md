# Native Wallet Adapter Demo

## Installation

### Step 1: Use edge `wallet-adapter`

In a separate directory, check out, build, and link the `wallet-adapter` code.

```
# Get the code
git clone git@github.com:solana-labs/wallet-adapter.git
cd wallet-adapter

# Build from source
yarn && lerna bootstrap && lerna run build

# Offer built packages for local installation
(cd packages/core/base && yarn link) && \
  (cd packages/core/react && yarn link) && \
  (cd packages/starter/create-react-app-starter && yarn link) && \
  (cd packages/starter/example && yarn link) && \
  (cd packages/starter/material-ui-starter && yarn link) && \
  (cd packages/starter/nextjs-starter && yarn link) && \
  (cd packages/starter/react-ui-starter && yarn link) && \
  (cd packages/ui/ant-design && yarn link) && \
  (cd packages/ui/material-ui && yarn link) && \
  (cd packages/ui/react-ui && yarn link) && \
  (cd packages/wallets/bitkeep && yarn link) && \
  (cd packages/wallets/bitpie && yarn link) && \
  (cd packages/wallets/blocto && yarn link) && \
  (cd packages/wallets/clover && yarn link) && \
  (cd packages/wallets/coin98 && yarn link) && \
  (cd packages/wallets/coinhub && yarn link) && \
  (cd packages/wallets/exodus && yarn link) && \
  (cd packages/wallets/glow && yarn link) && \
  (cd packages/wallets/huobi && yarn link) && \
  (cd packages/wallets/ledger && yarn link) && \
  (cd packages/wallets/mathwallet && yarn link) && \
  (cd packages/wallets/phantom && yarn link) && \
  (cd packages/wallets/safepal && yarn link) && \
  (cd packages/wallets/slope && yarn link) && \
  (cd packages/wallets/solflare && yarn link) && \
  (cd packages/wallets/sollet && yarn link) && \
  (cd packages/wallets/solong && yarn link) && \
  (cd packages/wallets/tokenpocket && yarn link) && \
  (cd packages/wallets/torus && yarn link) && \
  (cd packages/wallets/walletconnect && yarn link) && \
  (cd packages/wallets/wallets && yarn link)
```

### Step 2: Build the demo from source

```
cd js/
yarn && lerna bootstrap
(cd packages/wallet-adapter-native && yarn link @solana/wallet-adapter-base)
lerna run build
```
