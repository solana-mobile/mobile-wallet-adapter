# Mobile Wallet Adapter

[![Release (latest by date)](https://img.shields.io/github/v/release/solana-mobile/mobile-wallet-adapter)](https://github.com/solana-mobile/mobile-wallet-adapter/releases/latest)
[![Android CI](https://github.com/solana-mobile/mobile-wallet-adapter/actions/workflows/android.yml/badge.svg)](https://github.com/solana-mobile/mobile-wallet-adapter/actions/workflows/android.yml)
[![Sample App: clientlib-ktx](https://github.com/solana-mobile/mobile-wallet-adapter/actions/workflows/example-ktx.yml/badge.svg)](https://github.com/solana-mobile/mobile-wallet-adapter/actions/workflows/example-ktx.yml)
[![Specification CI](https://github.com/solana-mobile/mobile-wallet-adapter/actions/workflows/spec.yml/badge.svg)](https://github.com/solana-mobile/mobile-wallet-adapter/actions/workflows/spec.yml)

_Part of the [Solana Mobile Stack](https://github.com/solana-mobile/solana-mobile-stack-sdk)_

Join us on [Discord](https://discord.gg/solanamobile)

## See our [documentation website](https://docs.solanamobile.com/) for:
- [General Development Setup](https://docs.solanamobile.com/getting-started/development-setup)
- React Native
    - [Setup](https://docs.solanamobile.com/react-native/setup)
    - [Quickstart](https://docs.solanamobile.com/react-native/quickstart)
    - [Solana Mobile Stack Overview](https://docs.solanamobile.com/getting-started/overview)
- Android
    - [Setup](https://docs.solanamobile.com/android-native/setup)
    - [Quickstart](https://docs.solanamobile.com/android-native/quickstart)
- Unity
    - [Overview](https://docs.solanamobile.com/unity/unity_sdk)
- [Sample Apps](https://docs.solanamobile.com/sample-apps/sample_app_overview)


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
- A JavaScript [convenience wrapper](js/packages/mobile-wallet-adapter-protocol-web3js) that lets you use familiar datatypes from `@solana/web3.js` as inputs to the the Mobile Wallet Adapter protocol.
- A JavaScript [mobile wallet adapter plugin](js/packages/wallet-adapter-mobile) for use with the [Solana wallet adapter](https://github.com/solana-labs/wallet-adapter)
- An [example web app](examples/example-web-app) that demonstrates how to use the mobile wallet adapter plugin to sign messages and send transactions
- An [example React Native app](examples/example-react-native-app) that demonstrates how to use the Mobile Wallet Adapter protocol to interact with a mobile wallet

## How to build

All Android projects within this repository can be built using [Android Studio](https://developer.android.com/studio)

Documentation site:
- [General Development Setup](https://docs.solanamobile.com/getting-started/development-setup)

### How to reference these libraries in your project

#### For dApps:

For a more thorough setup guide, see our documentation site:
- Android:
    - [Setup](https://docs.solanamobile.com/android-native/setup)
- React Native:
    - [Setup](https://docs.solanamobile.com/react-native/setup)

Java
```
dependencies {
    implementation 'com.solanamobile:mobile-wallet-adapter-clientlib:2.0.0'
}
```

Kotlin
```
dependencies {
    implementation 'com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.0'
}
```

#### For wallets:

```
dependencies {
    implementation 'com.solanamobile:mobile-wallet-adapter-walletlib:2.0.0'
}
```

## Get involved

Contributions are welcome! Go ahead and file Issues, open Pull Requests, or join us on our [Discord](https://discord.gg/solanamobile) to discuss this SDK.
