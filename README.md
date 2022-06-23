# Mobile Wallet Adapter

_Part of the [Solana Mobile Stack](https://github.com/solana-mobile/solana-mobile-stack-sdk)_

Join us on [Discord](https://discord.gg/solanamobile)

## Summary

The Mobile Wallet Adapter specification, Android and JavaScript reference implementations, a demo wallet and dapps, and related documentation.

## Target audience

This repository is intended for consumption by Solana mobile developers.

## What's included

- The [Mobile Wallet Adapter protocol specification](https://solana-mobile.github.io/mobile-wallet-adapter/spec/spec.html)
- An [integration guide](android/docs/integration_guide.md) for Android wallets and dapps
- An Android library for [wallets](android/walletlib) to provide Mobile Wallet Adapter transaction signing services to dapps
- An Android library for [dapps](android/clientlib) to consume Mobile Wallet Adapter transaction signing services
- A [fake wallet](android/fakewallet) and a [fake dapp](android/fakedapp) demonstrating how to integrate walletlib and clientlib
- A JavaScript [reference implementation](js/packages/mobile-wallet-adapter-protocol) of the Mobile Wallet Adapter protocol
- A JavaScript [mobile wallet adapter plugin](js/packages/wallet-adapter-mobile) for use with the [Solana wallet adapter](https://github.com/solana-labs/wallet-adapter)
- An [example web app](js/packages/example-web-app) that demonstrates how to use the mobile wallet adapter plugin to sign messages and send transactions

## How to build

All Android projects within this repository can be built using [Android Studio](https://developer.android.com/studio)

### How to reference these libraries in your project

_Check back soon! We plan to publish the [walletlib](android/walletlib) and [clientlib](android/clientlib) libraries on Maven Central._

## Get involved

Contributions are welcome! Go ahead and file Issues, open Pull Requests, or join us on our [Discord](https://discord.gg/solanamobile) to discuss this SDK.
