# `@solana-mobile/wallet-adapter-mobile`

This is a plugin for use with [`@solana/wallet-adapter`](https://github.com/solana-labs/wallet-adapter). It enables apps to use a native wallet app on a mobile device to sign messages and transactions, and to send transactions if the wallet offers support for sending transactions.

![A screenshot showing the Solana Mobile wallet adapter in use with the wallet adapter dialog](https://user-images.githubusercontent.com/13243/174880433-92486385-6f9a-4221-bb55-c05bab057be6.png)

## Usage

Create an instance of the mobile wallet adapter like this.

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
});
```

Use that adapter instance alongside the other adapters used by your app.

```typescript
const wallets = useMemo(() => [
    new SolanaMobileWalletAdapter({
        addressSelector: createDefaultAddressSelector(),
        appIdentity: {
            name: 'My app',
            uri: 'https://myapp.io',
            icon: 'relative/path/to/icon.png',
       },
        authorizationResultCache: createDefaultAuthorizationResultCache(),
        cluster: WalletAdapterNetwork.Devnet,
    });
    new PhantomWalletAdapter(),
    /* ... other wallets ... */
]);

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