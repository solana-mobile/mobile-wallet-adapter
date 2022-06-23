# `@solana-mobile/mobile-wallet-adapter-protocol`

This is a reference implementation of the [Mobile Wallet Adapter specification](https://github.com/solana-mobile/mobile-wallet-adapter/blob/main/spec/spec.md) in JavaScript. Use this to start a session with a mobile wallet in which you can issue API calls to it (eg. `sign_message`) as per the spec.

If you are simply looking to integrate a JavaScript application with mobile wallets, see [`@solana-mobile/wallet-adapter-mobile`](https://www.npmjs.com/package/@solana-mobile/wallet-adapter-mobile) instead.

## Quick start

Use this API to start a session:

```typescript
import {withLocalWallet} from '@solana-mobile/mobile-wallet-adapter-protocol';

await withLocalWallet(async (wallet) => {
    /* ... */
});
```

The callback you provide will be called once a session has been established with a wallet. It will recieve the `MobileWallet` API as an argument. You can call protocol-specified methods using this function. Whatever you return from this callback will be returned by `withLocalWallet`.

```typescript
const signedPayloads = await withLocalWallet(async (wallet) => {
    const {signed_payloads} = await wallet('sign_message', {
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
    await withLocalWallet(async (wallet) => {
        try {
            await wallet('sign_transaction', /* ... */);
        } catch (e) {
            if (e instanceof SolanaMobileWalletAdapterProtocolReauthorizeError) {
                console.error('The auth token has gone stale');
                await wallet('reauthorize', {auth_token});
                // Retry...
            }
            throw e;
        }
    });
} catch(e) {
    if (e instanceof SolanaMobileWalletAdapterWalletNotInstalledError) {
        /* ... */
    }
    throw e;
}
```