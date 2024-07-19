# `@solana-mobile/mobile-wallet-adapter-walletlib`

This is a package that provides React Native bridge for the native `mobile-wallet-adapter-walletlib` library and it is designed for *Wallet apps* built in React Native. It provides an API to implement the wallet endpoint of the [mobile wallet adapter protocol](https://github.com/solana-mobile/mobile-wallet-adapter/blob/main/spec/spec.md).

Deep dive and read the full Mobile Wallet Adapter protocol [specification](https://solana-mobile.github.io/mobile-wallet-adapter/spec/spec.html#mobile-wallet-adapter-specification).

## Note
This package is still in alpha and is not production ready. However, the API is stable and will not change drastically, so you can begin integration with your wallet.


## Quickstart

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

// 1. Use a React hook API to begin listening for MWA events and initalize the session
useMobileWalletAdapterSession(
  'Example Wallet Label',
  config,
  handleRequest,
  handleSessionEvent,
);
```

Alternatively and for more flexibility, you can invoke individual methods to listen for MWA events (`initializeMWAEventListener`) and initialize the session (`initializeMWASession`).

#### initializeMWAEventListener

- Registers a listener for MWA Requests and MWA Session Events, using the provided handlers.
- You should ensure the listener is cleaned up, when it is out of scope (e.g `listener.remove()` on dismount).

#### initializeMWASession

- Establishes a session with the dApp endpoint and begins transmission of MWA requests/events.
- This should be called *after* `initializeMWAEventListener` is called, to ensure no events are missed.

#### Example: 

```ts
useEffect(() => {
  const config: MobileWalletAdapterConfig = {
      supportsSignAndSendTransactions: true,
      maxTransactionsPerSigningRequest: 10,
      maxMessagesPerSigningRequest: 10,
      supportedTransactionVersions: [0, 'legacy'],
      noConnectionWarningTimeoutMs: 3000,
    };

  // MWA Session Handlers
  const handleRequest = (request: MWARequest) => {
    /* ... */
  };
  const handleSessionEvent = (sessionEvent: MWASessionEvent) => {
    /* ... */
  };

  async function initializeMWASession() {
    try {
      const sessionId = await initializeMobileWalletAdapterSession(
        'wallet label',
        config,
      );
      console.log('sessionId: ' + sessionId);
    } catch (e) {
      console.error(e);
    }
  }
  const listener = initializeMWAEventListener(
    handleRequest,
    handleSessionEvent,
  );
  initializeMWASession();

  return () => listener.remove();
}, []);
```

### 2. Handling requests

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

There are a a selection of "fail" responses that you can return to the dApp. These are for cases where the user declines, or an error occurs during signing, etc.
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