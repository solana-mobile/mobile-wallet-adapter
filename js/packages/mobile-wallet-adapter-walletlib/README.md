# `@solana-mobile/mobile-wallet-adapter-walletlib`

This is a package that provides React Native bridge for the native `mobile-wallet-adapter-walletlib` library and it is designed for *Wallet apps* built in React Native. It provides an API to implement the wallet endpoint of the [mobile wallet adapter protocol](https://github.com/solana-mobile/mobile-wallet-adapter/blob/main/spec/spec.md).

Deep dive and read the full Mobile Wallet Adapter protocol [specification](https://solana-mobile.github.io/mobile-wallet-adapter/spec/spec.html#mobile-wallet-adapter-specification).

## Note
This package is still in alpha and is not production ready. However, the API is stable and will not change drastically, so you can begin integration with your wallet.


## Quickstart

### 1. Initialize the MWA event listener

Use the following API to start listening for MWA requests and events, and register request handlers.

```ts
import {
  initializeMWAEventListener,
  MWARequest,
  MWASessionEvent,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

const listener: EmitterSubscription = initializeMWAEventListener(
  (request: MWARequest) => { /* ... */ },
  (sessionEvent: MWASessionEvent) => { /* ... */ },
);

/* ... */

// Clean up the listener when it is out of scope
listener.remove()
```

You should ensure the listener is cleaned up with `listener.remove()` when it goes out of scope (e.g `listener.remove()` on component lifecycle unmount). 

### 2. Initialize the MWA session

Define your wallet config and use `initializeMWASession` to establish a session with the dApp endpoint and begin emission of MWA requests/events. 

> **Note:** This should be called *after* `initializeMWAEventListener` is called, to ensure no events are missed.

```ts
const config: MobileWalletAdapterConfig = {
  supportsSignAndSendTransactions: true,
  maxTransactionsPerSigningRequest: 10,
  maxMessagesPerSigningRequest: 10,
  supportedTransactionVersions: [0, 'legacy'],
  noConnectionWarningTimeoutMs: 3000,
  optionalFeatures: ['solana:signInWithSolana']
};

try {
  const sessionId = await initializeMobileWalletAdapterSession(
    'Wallet Name',
    config,
  );
  console.log('sessionId: ' + sessionId);
} catch (e: any) {
    if (e instanceof SolanaMWAWalletLibError) {
      console.error(e.name, e.code, e.message);
    } else {
      console.error(e);
    }   
}
```

> **Note**: Although, the `initializeMobileWalletAdapterSession` method returns a `sessionId`, this library only supports one active session for now.

### Example usage:

```ts
// When your MWA entrypoint is loaded, call a `useEffect` to kick off the listener and session.
useEffect(() => {
  async function initializeMWASession() {
    const config: MobileWalletAdapterConfig = {
      supportsSignAndSendTransactions: true,
      maxTransactionsPerSigningRequest: 10,
      maxMessagesPerSigningRequest: 10,
      supportedTransactionVersions: [0, 'legacy'],
      noConnectionWarningTimeoutMs: 3000,
    };
    try {
      const sessionId = await initializeMobileWalletAdapterSession(
        'Wallet Name',
        config,
      );
      console.log('sessionId: ' + sessionId);
    } catch (e: any) {
        if (e instanceof SolanaMWAWalletLibError) {
          console.error(e.name, e.code, e.message);
        } else {
          console.error(e);
        }   
    }
  }
  const listener = initializeMWAEventListener(
    (request: MWARequest) => { /* ... */ },
    (sessionEvent: MWASessionEvent) => { /* ... */ },
  );
  initializeMWASession();

  // When the component is unmounted, clean up the listener.
  return () => listener.remove();
}, []);
```

### 3. Handling requests and events

A `MWARequest` is handled by calling `resolve(request, response)` and each request have their appropriate response types.

An example of handling an `AuthorizationRequest`:
```typescript
import {
  AuthorizeDappResponse
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

const response = {
  publicKey: Keypair.generate().publicKey.toBytes(),
  label: 'Wallet Label',
} as AuthorizeDappResponse;

resolve(authorizationRequest, response)
```

There are a a selection of "fail" responses that you can return to the dApp. These are for cases where the user declines, or an error occurs during signing, etc.
```typescript
import {
  UserDeclinedResponse
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

const response = {
  failReason: MWARequestFailReason.UserDeclined,
} as UserDeclinedResponse;

// Tells the dApp user has declined the authorization request
resolve(authorizationRequest, response)
```

## Properties of an MWA Request
Each MWA Request is defined in [`resolve.ts`](https://github.com/solana-mobile/mobile-wallet-adapter/blob/main/js/packages/mobile-wallet-adapter-walletlib/src/resolve.ts#L38). 
Each come with their own properties and completion response structures.

If you want to understand the dApp perspective and how a dApp would send these requests, see [MWA API Documentation](https://docs.solanamobile.com/reference/) for dAppstypescript/mobile-wallet-adapter.

## MWARequest Interfaces

### `IMWARequest`
This is the base interface that all MWARequsts inherit from. The fields defined here are used in the package's internal implementation and the package consumer will generally not use them.

Fields:
- `__type`: An enum defining the type of MWA Request it is.
- `requestId`: A unique identifier of this specific MWA Request
- `sessionId`: A unique identifier of the MWA Session this request belongs to.

### `IVerifiableIdentityRequest`
This an interface that describes MWA Requests that come with a verifiable identity and the following 3 fields.

Fields:
- `authorizationScope`: A byte representation of the authorization token granted to the dApp.
- `cluster`: The Solana RPC cluster that the dApp intends to use.
- `appIdentity`: An object containing 3 optional identity fields about the dApp:
    - Note: The `iconRelativeUri` is a relative path, relative to `identityUri`.
```
{
  identityName: 'dApp Name',
  identityUri:  'https://yourdapp.com'
  iconRelativeUri: "favicon.ico", // Full path resolves to https://yourdapp.com/favicon.ico
}
```

### MWARequest Types

- `AuthorizeDappRequest`
  - [Spec](https://solana-mobile.github.io/mobile-wallet-adapter/spec/spec.html#authorize)
  - Interfaces: `IMWARequest`

- `ReauthorizeDappRequest`
  - [Spec](https://solana-mobile.github.io/mobile-wallet-adapter/spec/spec.html#reauthorize)
  - Interfaces: `IMWARequest`, `IVerifiableIdentityRequest`

- `DeauthorizeDappRequest`
  - [Spec](https://solana-mobile.github.io/mobile-wallet-adapter/spec/spec.html#deauthorize)
  - Interfaces: `IMWARequest`, `IVerifiableIdentityRequest`

- `SignMessagesRequest`
  - [Spec](https://solana-mobile.github.io/mobile-wallet-adapter/spec/spec.html#sign_messages)
  - Interfaces: `IMWARequest`, `IVerifiableIdentityRequest`

- `SignTransactionsRequest`
  - [Spec](https://solana-mobile.github.io/mobile-wallet-adapter/spec/spec.html#sign_transactions)
  - Interfaces: `IMWARequest`, `IVerifiableIdentityRequest`

- `SignAndSendTransactionsRequest`
  - [Spec](https://solana-mobile.github.io/mobile-wallet-adapter/spec/spec.html#sign_and_send_transactions)
  - Interfaces: `IMWARequest`, `IVerifiableIdentityRequest`