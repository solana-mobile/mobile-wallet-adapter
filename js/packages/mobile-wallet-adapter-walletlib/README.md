# `@solana-mobile/mobile-wallet-adapter-walletlib`

This is a package that provides React Native bridge for the native `mobile-wallet-adapter-walletlib` library and it is designed for *Wallet apps* built in React Native. It provides an API to implement the wallet endpoint of the [mobile wallet adapter protocol](https://github.com/solana-mobile/mobile-wallet-adapter/blob/main/spec/spec.md)

## Note
This package is still in alpha and is not production ready. However, the API is stable and will not change drastically, so you can begin integration with your wallet.

## Quickstart

### 1. Define your MWA entrypoint

To support bottom sheet signing flow, you need to define a React component to be the entrypoint for MWA. When the dApp sends out an intent for `solana-wallet://`, this entrypoint component will be rendered.

Define your entrypoint component in `index.js`:
```typescript
AppRegistry.registerComponent(
  'MobileWalletAdapterEntrypoint', // Constant
  () => YourMWAComponent,
);
```

### 1. Start listening and handling MWA requests

Use this API to start a session and start handling requests:
```typescript
import {
  MobileWalletAdapterConfig,
  MWARequest,
  MWASessionEvent,
  useMobileWalletAdapterSession,
} from '@solana-mobile/mobile-wallet-adapter-protocol-walletlib';

const config: MobileWalletAdapterConfig = useMemo(() => {
  return {
    supportsSignAndSendTransactions: true,
    maxTransactionsPerSigningRequest: 10,
    maxMessagesPerSigningRequest: 10,
    supportedTransactionVersions: [0, 'legacy'],
    noConnectionWarningTimeoutMs: 3000,
  };
}, []);

// MWA Session Handlers
const handleRequest = useCallback((request: MWARequest) => {
  /* ... */
}, []);

const handleSessionEvent = useCallback((sessionEvent: MWASessionEvent) => {
  /* ... */
}, []);

// Connect to the calling dApp and begin handling dApp requests
useMobileWalletAdapterSession(
  'Example Wallet Label',
  config,
  handleRequest,
  handleSessionEvent,
);
```

### 3. Handling requests

A `MWARequest` is handled by calling `resolve(request, response)` and each request have their appropriate response types.

An example of handling an `AuthorizationRequest`:
```typescript
import {
  AuthorizeDappResponse
} from '@solana-mobile/mobile-wallet-adapter-protocol-walletlib';

const response = {
  publicKey: Keypair.generate().publicKey.toBytes(),
  label: 'Wallet Label',
} as AuthorizeDappResponse;

resolve(authorizationRequest, response)
```

There are a a selection of "fail" responses that you can return to the dApp. These are for cases where the user
```typescript
import {
  UserDeclinedResponse
} from '@solana-mobile/mobile-wallet-adapter-protocol-walletlib';

const response = {
  failReason: MWARequestFailReason.UserDeclined,
} as UserDeclinedResponse;

// Tells the dApp user has declined the authorization request
resolve(authorizationRequest, response)
```
  

