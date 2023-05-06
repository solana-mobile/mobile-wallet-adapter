# `@solana-mobile/wallet-adapter-mobile`

This is a plugin for use with [`@solana/wallet-adapter`](https://github.com/solana-labs/wallet-adapter). It enables apps to use a native wallet app on a mobile device to sign messages and transactions, and to send transactions if the wallet offers support for sending transactions.

## Usage

Users of these libraries do not need to take any extra steps:

* `@solana/wallet-adapter-react@">=0.15.21"`

Those libraries automatically bundle the Mobile Wallet Adapter plugin, and enable it when running in a compatible mobile environment.

## Advanced usage

Developers might wish to customize the behavior of this plugin for their app. Specifying the app's name and icon, deciding which address to select in the event the wallet authorizes the app to use more than one, specifying which network cluster to communicate with, and more are made possible by creating an instance of the mobile wallet adapter like this.

```typescript
new SolanaMobileWalletAdapter({
    addressSelector: createDefaultAddressSelector(),
    appIdentity: {
        name: 'My app',
        uri: 'https://myapp.io',
        icon: 'relative/path/to/icon.png',
    },
    authorizationResultCache: createDefaultAuthorizationResultCache(),
    cluster: WalletAdapterNetwork.Devnet,
    onWalletNotFound: createDefaultWalletNotFoundHandler(),
});
```

Developers who use `@solana/wallet-adapter-react@">=0.15.21"` can supply this custom instance to `WalletProvider` which will use it to override the default one. 

```typescript
const wallets = useMemo(
    () => [
        new SolanaMobileWalletAdapter({
            addressSelector: createDefaultAddressSelector(),
            appIdentity: {
                name: 'My app',
                uri: 'https://myapp.io',
                icon: 'relative/path/to/icon.png',
            },
            authorizationResultCache: createDefaultAuthorizationResultCache(),
            cluster: WalletAdapterNetwork.Devnet,
            onWalletNotFound: createDefaultWalletNotFoundHandler(),
        }),
    ],
    [],
);

return (
    <ConnectionProvider endpoint={clusterApiUrl(WalletAdapterNetwork.Devnet)}>
        <WalletProvider wallets={wallets}>
            <MyApp />
        </WalletProvider>
    </ConnectionProvider>
)
```

For more information about how to use wallet adapter plugins, visit https://github.com/solana-labs/wallet-adapter

## Configuration

### App identity

The `AppIdentity` config identifies your app to a native mobile wallet. When someone connects to a wallet for the first time, the wallet may present this information in the on-screen prompt where the ask if the visitor would like to authorize your app for use with their account.

- `name` &ndash; The plain-language name of your application.
- `uri` &ndash; The uri of your application. This uri may be required to participate in [dApp identity verification](https://github.com/solana-mobile/mobile-wallet-adapter/blob/main/spec/spec.md#dapp-identity-verification) as part of the mobile wallet adapter protocol specification.
- `icon` &ndash; An icon file path, relative to the `uri`.

### Address selector

The Mobile Wallet Adapter specification allows a wallet to authorize a dApp to use one or more addresses. dApps must supply code to select a single address for use in the adapter. That code must conform to the `AddressSelector` interface.

```typescript
export interface AddressSelector {
    select(addresses: Base64EncodedAddress[]): Promise<Base64EncodedAddress>;
}
```

Alternatively, you can use the included `createDefaultAddressSelector()` method to create a selector that always chooses the first address in the list.

### Authorization result cache

The first time that someone authorizes a native wallet app for use with your application, you should cache that authorization for future use. You can supply your own implementation that conforms to the `AuthorizationResultCache` interface.

```typescript
export interface AuthorizationResultCache {
    clear(): Promise<void>;
    get(): Promise<AuthorizationResult | undefined>;
    set(authorizationResult: AuthorizationResult): Promise<void>;
}
```

Alternatively, you can use the included `createDefaultAuthorizationResultCache()` method to create a cache that reads and writes the adapter's last-obtained `AuthorizationResult` to your browser's local storage, if available.

### Cluster

Each authorization a dApp makes with a wallet is tied to a particular Solana cluster. If a dApp wants to change the cluster on which to transact, it must seek an authorization for that cluster.

### Wallet-not-found handler

When you call `connect()` but no wallet responds within a reasonable amount of time, it is presumed that no compatible wallet is installed. You must supply an `onWalletNotFound` function to handle this case.

Alternatively, you can use the included `createDefaultWalletNotFoundHandler()` method to create a function that opens the Solana Mobile ecosystem wallets webpage.

## Android Chrome Browser Issues
Chrome on Android has a policy of blocking all navigation that does not come from explicit user gestures (click, tap, swipe, keypress). As a result, MWA Intent navigation to a wallet app will be blocked if it does not come from a user gesture. 

You will see an error like:

```
Navigation is blocked: solana-wallet:/v1/associate...
```

There isn't a way around this on the Android Chrome Browser, but you can write a mobile app if you need this behavior.


