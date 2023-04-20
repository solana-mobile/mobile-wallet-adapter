# `@solana-mobile/mobile-wallet-adapter-protocol`

This is a reference implementation of the [Mobile Wallet Adapter specification](https://github.com/solana-mobile/mobile-wallet-adapter/blob/main/spec/spec.md) in JavaScript. Use this to start a session with a mobile wallet in which you can issue API calls to it (eg. `sign_messages`) as per the spec.

If you are simply looking to integrate a JavaScript application with mobile wallets, see [`@solana-mobile/wallet-adapter-mobile`](https://www.npmjs.com/package/@solana-mobile/wallet-adapter-mobile) instead.

## Learn how to use this API on our [documentation website](https://docs.solanamobile.com/):
- React Native
    - [Quickstart Setup](https://docs.solanamobile.com/react-native/quickstart)
    - [dApp Integration Guide](https://docs.solanamobile.com/react-native/mwa_integration_rn)
    - [Hello World Tutorial](https://docs.solanamobile.com/getting-started/hello_world_tutorial)
- [Sample App Reference](https://docs.solanamobile.com/sample-apps/sample_app_overview)

## Quick start

Use this API to start a session:

```typescript
import {transact} from '@solana-mobile/mobile-wallet-adapter-protocol';

await transact(async (wallet) => {
    /* ... */
});
```

The callback you provide will be called once a session has been established with a wallet. It will recieve the `MobileWallet` API as an argument. You can call protocol-specified methods using this function. Whatever you return from this callback will be returned by `transact`.

```typescript
const signedPayloads = await transact(async (wallet) => {
    const {signed_payloads} = await wallet.signMessages({
        auth_token,
        payloads: [/* ... */],
    });
    return signed_payloads;
});
```

The wallet session will stay active until your callback returns. Typically, wallets will redirect back to your app once the session ends.

## Exception handling

You can catch exceptions at any level. See `errors.ts` for a list of exceptions that might be thrown.

```typescript
try {
    await transact(async (wallet) => {
        try {
            await wallet.signTransactions(/* ... */);
        } catch (e) {
            if (
                e instanceof SolanaMobileWalletAdapterProtocolError &&
                e.code === SolanaMobileWalletAdapterProtocolErrorCode.ERROR_REAUTHORIZE
            ) {
                console.error('The auth token has gone stale');
                await wallet.reauthorize({auth_token, identity});
                // Retry...
            }
            throw e;
        }
    });
} catch(e) {
    if (
        e instanceof SolanaMobileWalletAdapterError &&
        e.code === SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND
    ) {
        /* ... */
    }
    throw e;
}
```