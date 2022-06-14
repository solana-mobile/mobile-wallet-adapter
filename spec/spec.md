---
layout: default
title: Mobile Wallet Adapter specification
---

{%- comment -%}
Please don't introduce unnecessary line breaks in this specification - it's diff-unfriendly.
{%- endcomment -%}

# Mobile Wallet Adapter specification

1. TOC
{: toc}

# Version

This specification uses [semantic versioning](https://en.wikipedia.org/wiki/Software_versioning#Semantic_versioning)

**Version: 0.1.0**

## Changelog

| Version | Description |
| ------- | ----------- |
| 0.1.0   | Initial draft |

# Non-normative front matter

The contents of this section attempt to explain this specification with context and goals, but do not form a formal part of the specification itself.

## Summary

The goal of this specification is to define a protocol to expose Solana iOS and Android wallet app functionality (authorization and transaction signing) to dapps. It is optimized for use cases where the wallet app is running on a mobile device (Android or iOS), but does not preclude usage by wallets on a desktop OS (Linux, macOS, Windows, etc). This protocol is freely available to all Solana developers, and the intention is for this to become the standard for connecting all dapps to mobile wallet apps.

## User stories

These user stories outline the goals and user experiences that this protocol aims to enable.

1. As a user, I want my phone to be a universal wallet for any transaction, regardless of where I am interacting with a dapp (i.e. on a single device, or on a different nearby device)
1. As a user, I want my dapp transactions to be private and secure
1. As a user, I want local dapp authorizations and transactions to be as simple as those I experience using the web-based wallet-adapter
1. As a user, I want my authorization of a dapp by my wallet to be a one-time operation
1. As a native dapp developer, I want my dapp to work with all native wallet apps
1. As a web dapp developer, I want my mobile browser friendly dapp to work with all native wallet apps
1. As a dapp or wallet app developer, I want permissively licensed open source reference implementations of the protocol to be available for my use

## Requirements

These requirements are derived from the user stories, and exist to guide specification design decisions.

1. All communication between dapps and wallet apps should take place over a secure channel.

   _Rationale: this prevents transaction tampering and frontrunning._
1. At least one transport mechanism should be a standard web technology (for e.g. HTTP/S, WebSockets, etc).
   
   _Rationale: this supports using web-based dapps with native wallet apps._
1. The protocol must support one or more mechanisms to exchange a shared token (for e.g., URI links, QR codes, NFC, BT, etc), which shall be used to establish the secure channel. 
   
   _Rationale: this protects against MITM attacks during secure channel establishment._
1. At least one shared token exchange mechanism must be available to remote browser-based dapps (e.g. displaying a QR code).
   
   _Rationale: this supports using remote dapps with native wallet apps._
1. The shared token must be able to establish a secure channel an indefinite number of times. 
   
   _Rationale: this supports persistent dapp <-> wallet app connections after a one-time authorization._
1. For communication between a dapp and native wallet app running on a single device, the protocol must not require the use of a remote communication intermediary (though this does not preclude the optional use of one, at the discretion of the dapp and/or wallet app). 
   
   _Rationale: a remote intermediate (such as a reflector server) increases both the communication latency and the attack surface (by intentionally introducing a 3rd party to communications)._
1. The protocol must support a mechanism (such as a custom URI scheme) that allows launching a native app from a mobile web browser on both Android and iOS.
   
   _Rationale: the user should not be required to manually launch a wallet app for local wallet app interaction._
1. The dapp must identify itself to the wallet app during authorization. 
   
   _Rationale: this allows the wallet app to give the user context about what they are authorizing._
1.  The reference implementation(s) of the protocol should be Apache 2.0 licensed, and made available on a public repository.

# Specification

## Terminology

_association token_ - a base64url encoding of an ECDSA public keypoint

_dapp endpoint_ - an app implementing user-facing functionality (e.g. DeFi services, blockchain games, etc). This endpoint acts as both the initiator and the client in this protocol.

_reflector_ - an intermediary which brokers connections between two endpoints, when they are unable to communicate directly themselves. It should be viewed as a potential adversary.

_wallet endpoint_ - an app implementing wallet-like functionality (i.e. providing transaction signing services). This endpoint acts as the server in this protocol.

## Transport

### WebSockets

WebSockets is a mandatory transport protocol for mobile-wallet-adapter implementations. Dapp endpoints must be able to act as a WebSocket client, and wallet endpoints must be able to act as either a WebSocket server or client (depending on whether the connection is local, or if a reflector is used, respectively).

A WebSocket client API is available in all major browsers, enabling web-based dapps (both mobile and desktop) to use this transport.

When connecting to a [Local URI](#local-uri), the dapp endpoint must request, and the wallet endpoint must respond with, the `com.solana.mobilewalletadapter.v1` WebSocket subprotocol. When connecting to a [Remote URI](#remote-uri), the both the dapp and wallet endpoints must request, and the reflector server must respond with, both the `com.solana.mobilewalletadapter.v1` and `com.solana.mobilewalletadapter.v1.reflector` WebSocket subprotocols.

When the wallet endpoint is acting as a WebSocket server, it must send periodic [`PING`](https://datatracker.ietf.org/doc/html/rfc6455#section-5.5.2) frames to the dapp endpoint.

### Bluetooth LE

Bluetooth LE is an optional transport protocol for mobile-wallet-adapter implementations. It enforces a proximity requirement on the endpoints, which some users may find desirable from a security standpoint. It also eliminates the need for a reflector when connecting dapp and wallet endpoints running on different systems, removing an attack surface from the protocol.

A Web Bluetooth client API is available in many browsers, enabling web-based dapps (both mobile and desktop) to use this transport.

The details of this transport are not defined in this version of the mobile-wallet-adapter protocol. It is expected to be defined in a future protocol version.

## Association

Association is the process of establishing a shared association identifier between a dapp endpoint and a wallet endpoint (with no requirement that these be running on the same system). An association is ephemeral - it persists only until the transport is disconnected. A new association is performed every time a dapp endpoint seeks to connect to a wallet endpoint.

### Association keypair

The dapp endpoint should generate an ephemeral EC keypair on the P-256 curve, and encode the public keypoint Qa using the X9.62 public key format `(0x04 || x || y)`. This public keypoint is then base64url-encoded, and the resulting string is called the association token. The private keypoint for this keypair will be used during session establishment.

### Local URI

When running on Android or iOS, the dapp endpoint should first attempt to associate with a local wallet endpoint by opening a URI (either from within the browser for a web dapp, or directly from a native dapp) with the `solana-wallet:` scheme. The URI should be formatted as:

```
solana-wallet:/v1/associate/local?association=<association_token>&port=<port_number>
```

where:

- `association_token` is as described above
- `port_number` is a random number between 49152 and 65535

Once the URI is opened, the dapp endpoint should attempt to connect to the local WebSocket address, `ws://localhost:<port_number>/solana-wallet`, and proceed to [Session establishment](#session-establishment).

If the WebSocket transport is not available locally after X seconds, the dapp endpoint should display user guidance (e.g. download a wallet) and present the opportunity to connect to a remote wallet endpoint using one or more of the other association mechanisms.

#### Android

If a wallet endpoint is installed which has registered an Activity for this URI scheme and format, it will be launched. Upon launch via this URI, the wallet endpoint should start a WebSocket server on port `port_number` and begin listening for connections to `/solana-wallet` for X seconds. This websocket server should only accept connections from the localhost.

Whether launched from a web browser or a native dapp endpoint, the Intent’s action will be [`android.intent.action.VIEW`](https://developer.android.com/reference/android/content/Intent#ACTION_VIEW) and the category will be [`android.intent.category.BROWSABLE`](https://developer.android.com/reference/android/content/Intent#CATEGORY_BROWSABLE). When launched by a web browser, no caller identity will be available, and as such, the referrer details available within the Intent cannot be used to verify the origin of the association. When launched by a native dapp endpoint, this Intent should be sent with [`startActivityForResult`](https://developer.android.com/reference/android/app/Activity#startActivityForResult(android.content.Intent,%20int)), allowing the wallet endpoint to query the caller identity. The result returned to the calling dapp endpoint is not specified.

#### iOS

__TODO__

#### Desktop

Since desktop OSes do not generally allow launching an app with a URI, a dapp endpoint should not attempt to use this association scheme. One or more of the other association mechanisms should be utilized instead.

### Remote URI

When running on a desktop OS, or when connecting to a local wallet endpoint fails, the dapp endpoint may present a URI suitable for connection via a [reflector WebSocket server](#reflector-protocol), which will reflect traffic between two parties. The URI should be formatted as:

```
solana-wallet:/v1/associate/remote?association=<association_token>&reflector=<host_authority>&id=<reflector_unique_id>
```

where:

- `association_token` is as described above
- `host_authority` is the address of a publicly routable WebSocket server implementing the reflector protocol
- `reflector_unique_id` is a random number generated by the dapp endpoint, 0 ≤ n ≤ 2^53 - 1

This URI should be provided to the wallet endpoint through an out-of-band mechanism, detailed in the subsections below. Each of the dapp and wallet endpoints should attempt to connect to the WebSocket address `wss://<host_authority>/reflect?id=<reflector_unique_id>`. On connection, each endpoint should wait for the [Reflector protocol](#reflector-protocol) to signal that the counterparty endpoint has connected.

The endpoints will each wait up to X seconds for reflection to commence. If it does not commence, the endpoints will disconnect and present appropriate error messages to the user.

#### QR codes

Dapp endpoints must support displaying the remote URI to the user encoded as a QR code. After displaying a QR code, the dapp endpoint should connect to the specified reflector. Wallet endpoints on devices with a camera should support scanning QR codes within the app, receiving notifications from the system that a QR code encoding a remote URI has been received, or both. Upon receipt of a remote URI from a scanned QR code, the wallet endpoint should attempt to connect to the specified reflector.

#### Clipboard

Dapp endpoints may optionally also support copying the remote URI to the system clipboard. After copying the remote URI to the clipboard, the dapp endpoint should connect to the specified reflector. Wallet endpoints on desktop OSes should provide a method to accept a remote URI from the system clipboard. Upon receipt of a remote URI from the system clipboard, the wallet endpoint should attempt to connect to the specified reflector.

### Endpoint-specific URIs

During [Session Establishment](#session-establishment), the wallet endpoint may return a URI prefix to use for future association attempts. This is expected to be used with [App Links](https://developer.android.com/training/app-links) or [Universal Links](https://developer.apple.com/ios/universal-links/), to ensure that the desired wallet app is launched by the dapp. If a dapp has been informed of a URI prefix for a wallet, it should use it with the same path elements and parameters provided as for the `solana-wallet:` URI scheme. For e.g., if an Android wallet endpoint handles App Links for solanaexamplewallet.io, it could provide a prefix of:

```
https://solanaexamplewallet.io/mobilewalletadapter
```

The dapp endpoint would then assemble the following URI to begin association with that wallet locally:

```
https://solanaexamplewallet.io/mobilewalletadapter/v1/associate/local?association=<association_token>&port=<port_number>
```

All other aspects of associating are identical to those specified in the relevant preceding section.

### Bluetooth LE

Bluetooth LE session establishment is not defined in this version of the mobile-wallet-adapter protocol. It is expected to be defined in a future protocol version.

## Session establishment

### APP_PING

While the expectation is that the WebSocket server be responsible for periodically issuing [`PING`s](https://datatracker.ietf.org/doc/html/rfc6455#section-5.5.2) to the client endpoint, the introduction of a reflector adds a requirement that the endpoints need to be notified when the counterparty is available.

An `APP_PING` is an empty message. It is sent by the reflector to each endpoint when both endpoints have connected to the reflector. On first connecting to a reflector, the endpoints should wait to receive this message before initiating any communications. After any other message has been received, the `APP_PING` message becomes a no-op, and should be ignored.

### HELLO_REQ

#### Direction

Dapp endpoint to wallet endpoint

#### Specification

```
<Qd><Sa>
```

where:

- `Qd`: the X9.62-encoded dapp endpoint ephemeral ECDH public keypoint
- `Sa`: a P1363-encoded ECDSA-SHA256 signature of Qd using the [association keypair](#association-keypair)

#### Description

The `HELLO_REQ` message is the first message sent after a connection is established between the endpoints, and begins a Diffie-Hellman-Merkle key exchange. The dapp endpoint generates an ephemeral P-256 EC keypair and X9.62-encodes the public keypoint `Qd`. This encoded public keypoint is then ECDSA-SHA256-signed with the private keypoint of the [association keypair](#association-keypair), and the P1363-encoded signature appended to the encoded `Qd` public keypoint to form the HELLO_REQ message. The private keypoint of the P-256 EC keypair is retained for use on receipt of the `HELLO_RSP` message.

On receipt, the wallet endpoint should verify the signature of `Qd` using the association token. If signature verification is successful, the wallet endpoint should prepare and send a `HELLO_RSP` message to the dapp endpoint.

If qd signature verification fails, if no `HELLO_REQ` message is received by the wallet endpoint within X seconds, or if a second `HELLO_REQ` message is received by the wallet endpoint at any time during the connection, all ephemeral key materials should be discarded, and the connection should be closed.

### HELLO_RSP

#### Direction

Wallet endpoint to dapp endpoint

#### Specification

```
<Qw>
```

where:

- `Qw`: the X9.62-encoded wallet endpoint ephemeral ECDH public keypoint

#### Description

In response to a valid `HELLO_REQ` message to the wallet endpoint (which should be the first message received after a connection is established between the endpoints), it should generate a P-256 EC keypair and X9.62-encode the public keypoint `Qw`. This encoded public keypoint `Qw` forms the `HELLO_RSP` message.

Upon sending of the `HELLO_RSP` message by the wallet endpoint, and receipt of the `HELLO_RSP` message by the dapp endpoint, each endpoint is now in possession of all necessary key materials to generate a shared secret for the [chosen encryption algorithm](#encrypted-message-wrapping), using the ECDH (as specified by [NIST SP 800-56A](https://csrc.nist.gov/publications/detail/sp/800-56a/rev-3/final)) and HKDF (as specified by [RFC5869](https://datatracker.ietf.org/doc/html/rfc5869)) algorithms with the following KDF parameters:

- `ikm`: the 32-byte ECDH-derived secret
- `salt`: the 65 byte X9.62-encoded public keypoint Qa of the [association keypair](#association-keypair)
- `L`: 16 bytes (such that the output of the HKDF may be used directly as an AES-128 key)

Once each endpoint has calculated the ephemeral shared secret, they should proceed to providing or consuming the [Wallet RPC interface](#wallet-rpc-interface).

If either public keypoint `Qd` or `Qw` is not valid, if no `HELLO_RSP` message is received by the dapp endpoint within X seconds, or if a second `HELLO_RSP` message is received by the dapp endpoint at any time during the connection, all ephemeral key materials should be discarded, and the connection should be closed.

## Wallet RPC interface

### Operation

After [session establishment](#session-establishment) completes, the wallet endpoint is ready to accept [JSON-RPC 2.0](https://www.jsonrpc.org/specification) method calls from the dapp endpoint. Dapp endpoints require a successful [`authorize`](#authorize) or [`reauthorize`](#reauthorize) method call before the [signing methods](#privileged-methods) will be available.

### Encrypted message wrapping

After the [session establishment](#session-establishment) process completes, every message received by an endpoint is expected to be encrypted with AES-128-GCM (as specified by [NIST SP 800-38D](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf)). The sending endpoint should prepare the encrypted message by concatenating:

- a random 12-byte IV (which should be newly generated for each encrypted message)
- the AES-128-GCM message ciphertext, encrypted using the private key created during [session establishment](#session-establishment) and the random IV
- the 16-byte authentication tag generated during AES-128-GCM encryption

After decrypting the ciphertext with the shared secret generated during [session establishment](#session-establishment) and verifying the authentication tag, the receiving endpoint should further interpret it as a [JSON-RPC 2.0](https://www.jsonrpc.org/specification) message.

#### Non-normative commentary

Why does the protocol specify this, rather than rely on, e.g., TLS?

- In remote usage, a reflector server is used to mediate a connection between a dapp and wallet endpoint. This reflector is viewed as an adversary, and so should not have access to the plaintext of endpoint communications.
- This protocol supports multiple transports (i.e. `ws:` for local connections, `wss:` for reflector connections, Bluetooth LE for wireless connections). Each of these has different confidentiality and authenticity guarantees. By encrypting messages at the application layer, the protocol can provide a uniform minimum security guarantee for a heterogeneous set of transports.

### Non-privileged methods

Non-privileged methods do not require the dapp endpoint to request access to them in a call to [`authorize`](#authorize) (though they may still require an `auth_token` be provided for their functionality).

#### authorize

##### JSON-RPC method specification

###### Method 
{: .no_toc }

```
authorize
```

###### Params
{: .no_toc }

```
{
    “identity”: {
        “uri”: “<dapp_uri>”,
        “icon”: “<dapp_icon_relative_path>”,
        “name”: “<dapp_name>”,
    },
    “privileged_methods”: [“<privileged_methods>”],
}
```

where:

- `dapp_uri`: (Optional) a URI representing the web address associated with the dapp endpoint making this authorization request. If present, it must be an absolute, hierarchical URI.
- `dapp_icon_relative_path`: (Optional) a relative path (from dapp_uri) to an image asset file of an icon identifying the dapp endpoint making this authorization request
- `dapp_name`: (Optional) the display name for this dapp endpoint
- `privileged_methods`: a list of method names from Privileged methods for which the dapp endpoint is requesting permissions

###### Result
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
    “pub_key”: “<public_key>
    “wallet_uri_base”: “<wallet_uri_base>”,
}
```

where:

- `auth_token`: an opaque string representing a unique identifying token issued by the wallet endpoint to the dapp endpoint. The format and contents are an implementation detail of the wallet endpoint. The dapp endpoint can use this on future connections to `reauthorize` access to [Signing methods](#privileged-methods).
- `public_key`: the base58-encoded public key for the account
- `wallet_uri_base`: (optional) if this wallet endpoint has an [endpoint-specific URI](#endpoint-specific-uris) that the dapp endpoint should use for subsequent connections, this member will be included in the result object. The dapp endpoint should use this URI for all subsequent connections where it expects to use this `auth_token`.

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_AUTHORIZATION_FAILED` if the wallet endpoint did not authorize access to the requested privileged methods

##### Description
This method allows the dapp endpoint to request authorization from the wallet endpoint for access to the specified [privileged methods](#privileged-methods). On success, it returns an `auth_token` providing access to those privileged methods. It may also return a URI suitable for future use as an [endpoint-specific URI](#endpoint-specific-uris).

The returned `auth_token` is an opaque string with meaning only to the wallet endpoint which created it. It is recommended that the wallet endpoint include a mechanism to authenticate the contents of auth tokens it issues (for e.g., with an HMAC, or by encryption with a secret symmetric key).

The lifetime of the returned `auth_token` is not defined. If a privileged method returns `ERROR_REAUTHORIZE`, the dapp endpoint should call the [`reauthorize`](#reauthorize) method to renew the token.

Dapp endpoints should make every effort possible to [verify the authenticity](#dapp-identity-verification) of the presented identity. While the `dapp_uri` parameter is optional, it is strongly recommended - without it, the wallet endpoint may not be able to verify the authenticity of the dapp.

#### deauthorize

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
deauthorize
```

###### Params
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
}
```

where:

- `auth_token`: an opaque string previously returned by a call to [`authorize`](#authorize), [`reauthorize`](#reauthorize), or [`clone_authorization`](#clone_authorization)

###### Result
{: .no_toc }

```
{}
```

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above

##### Description

This method will make the provided `auth_token` invalid for use (if it ever was valid). To avoid disclosure, this method will not indicate whether the `auth_token` was previously valid to the caller.

#### reauthorize

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
reauthorize
```

###### Params
{: .no_toc }

```
{
    “identity”: {
        “uri”: “<dapp_uri>”,
        “icon”: “<dapp_icon_relative_path>”,
        “name”: “<dapp_name>”,
    },
    “auth_token”: “<auth_token>”,
}
```

where:

- `identity`: see [`authorize`](#authorize)
- `auth_token`: an opaque string previously returned by a call to [`authorize`](#authorize), [`reauthorize`](#reauthorize), or [`clone_authorization`](#clone_authorization)

###### Result
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
}
```

where:

- `auth_token`: as defined for [`authorize`](#authorize)

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_AUTHORIZATION_FAILED` if the wallet endpoint declined to renew `auth_token` for any reason

##### Description

This method attempts to renew the specified `auth_token`. The meaning of renew is an implementation detail of the wallet endpoint; it may return the same token, issue a new token, or refuse to renew the token. If the result is `ERROR_AUTHORIZATION_FAILED`, the token could not be renewed. A new token will need to be requested with the [`authorize`](#authorize) method. If a new `auth_token` is returned, the dapp endpoint should assume that the old `auth_token` is no longer valid and discard it.

#### clone_authorization

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
clone_authorization
```

###### Params
{: .no_toc }

See [`reauthorize`](#reauthorize)

###### Result
{: .no_toc }

See [`reauthorize`](#reauthorize)

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `-32601` (Method not found) if [`clone_authorization`](#clone_authorization) is not supported by this wallet endpoint
- `ERROR_REAUTHORIZE` if `auth_token` requires [reauthorization](#reauthorize) before cloning
- `ERROR_AUTHORIZATION_FAILED` if the wallet endpoint declined to clone `auth_token` for any reason

##### Description

_Implementation of this method by a wallet endpoint is optional._

This method attempts to clone the specified `auth_token` in a form suitable for sharing with another instance of the dapp endpoint, possibly running on a different system. Whether or not the wallet endpoint supports cloning an `auth_token` is an implementation detail. If this method succeeds, it will return an `auth_token` appropriate for sharing with another instance of the same dapp endpoint. This new `auth_token` may require [reauthorization](#reauthorize) by the recipient to obtain a token suitable for use in the new context. Note that the recipient must also use the returned `wallet_uri_base` to ensure that a connection is made to the appropriate wallet endpoint context.

The original `auth_token` passed to this method remains valid, and the dapp endpoint should continue to use it.

###### Non-normative commentary

The clone_authorization method enables sharing of an authorization between related instances of a dapp endpoint (for example, running on a mobile device and a desktop OS). This is a sensitive operation; dapp endpoints must endeavor to transfer the token securely between dapp endpoint instances. The ability of wallet endpoints to validate the identity of the holder of the cloned token is an implementation detail, and may be weaker than that of the original token. As such, not all wallet endpoints are expected to support this feature.

### Privileged methods

#### sign_transaction

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
sign_transaction
```

###### Params
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
    “payloads”: [“<transaction>”, ...],
    "return_signed_payloads": <return_signed_payloads>
}
```

where:

- `auth_token`: an `auth_token` returned by [`authorize`](#authorize), [`reauthorize`](#reauthorize), or [`clone_authorization`](#clone_authorization) for which access to `sign_transaction` was requested
- `transaction`: one or more base64-encoded transaction payloads to sign, each with a maximum pre-encoding size of `MAX_TRANSACTION_SZ`
- `return_signed_payloads`: (optional) if present, a boolean value indicating whether the result should contain a `signed_payloads` entry. If not present, defaults to `false`.

###### Result
{: .no_toc }

```
{
    "signatures": ["<signature>", ...],
    “signed_payloads”: [“<signed_transaction>”, ...],
}
```

where:

- `signatures`: base64-encoded transaction signatures
- `signed_payloads`: (optional) base64-encoded signed transaction payloads, each with a maximum pre-encoding size of `MAX_TRANSACTION_SZ`. This will be present only if `return_signed_payloads` was present and `true` in params.

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_REAUTHORIZE` if `auth_token` requires [`reauthorization`](#reauthorize)
- `ERROR_AUTHORIZATION_FAILED` if the `auth_token` is invalid, or not authorized for `sign_transaction`
- `ERROR_INVALID_PAYLOAD`

  ```
  “data”: {
      “valid”: [<transaction_valid>, ...],
  }
  ```

  if any transaction does not represent a valid transaction for signing, where:

    - `transaction_valid`: an array of booleans with the same length as payloads indicating which are valid

- `ERROR_NOT_SIGNED` if the wallet endpoint declined to sign these transactions for any reason

##### Description

The wallet endpoint should attempt to simulate the transactions provided by data and present them to the user for approval (if applicable). If approved (or if it does not require approval), the wallet endpoint should sign the transactions with the private key for the authorized account, and return the signed transactions to the dapp endpoint.

#### sign_and_send_transaction

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
sign_and_send_transaction
```

###### Params
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
    “payloads”: [“<transaction>”, ...],
    “commitment”: “<commitment_level>”,
}
```

where:

- `auth_token`: an auth_token returned by [`authorize`](#authorize), [`reauthorize`](#reauthorize), or [`clone_authorization`](#clone_authorization) for which access to `sign_and_send_transaction` was requested
- `transaction`: one or more base64-encoded transaction payload to sign, each with a maximum pre-encoding size of `MAX_TRANSACTION_SZ`
- `commitment_level`: one of `processed`, `confirmed`, or `finalized`

###### Result
{: .no_toc }

```
{
    “signatures”: [“<transaction_signature>”, ...],
}
```

where:

- `transaction_signature`: the corresponding base58-encoded transaction signatures

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `-32601` (Method not found) if `sign_and_send_transaction` is not supported by this wallet endpoint
- `ERROR_REAUTHORIZE` if `auth_token` requires [`reauthorization`](#reauthorize)
- `ERROR_AUTHORIZATION_FAILED` if the `auth_token` is invalid, or not authorized for `sign_and_send_transaction`
- `ERROR_INVALID_PAYLOAD`

  ```
  “data”: {
      “valid”: [<transaction_valid>, ...]
  }
  ```

  if any transaction does not represent a valid transaction for signing, where:

  - `transaction_valid`: an array of booleans with the same length as `payloads` indicating which are valid
- `ERROR_NOT_SIGNED` if the wallet endpoint declined to sign this transaction for any reason
- `ERROR_NOT_COMMITTED`

  ```
  “data”: {
      “signatures”: [“<transaction_signature>”, ...],
      “commitment”: [<commitment_reached>, ...],
  }
  ```

  if the requested commitment level was not reached for any of the signed transactions, where:

  - `signatures`: as defined for a successful result
  - `commitment_reached`: for each entry in `signatures`, a boolean indicating whether the desired `commitment_level` was reached

##### Description

_Implementation of this method by a wallet endpoint is optional._

The wallet endpoint should attempt to simulate the transactions provided by `payloads` and present them to the user for approval (if applicable). If approved (or if it does not require approval), the wallet endpoint should sign the transactions with the private key for the authorized account, submit them to the network, wait for the requested commitment level to be reached, and return the transaction signatures to the dapp endpoint.

###### Non-normative commentary

This method is optional, to support signing-only wallet endpoints which do not have any form of network connectivity.

it does not allow the dapp endpoint to specify the network RPC server to submit the transaction to; that is at the discretion of the wallet endpoint. If this is a detail that matters to the dapp endpoint, it should instead use the `sign_transaction` method and submit the transaction to a network RPC server of its choosing.

It is recommended that dapp endpoints verify that the reported commitment level was actually reached for each transaction, to minimize the risks presented by malicious wallet endpoints.

#### sign_message

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
sign_message
```

###### Params
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
    “payloads”: [“<message>”, ...],
    "return_signed_payloads": <return_signed_payloads>
}
```

where:

- `auth_token`: an auth_token returned by [`authorize`](#authorize), [`reauthorize`](#reauthorize), or [`clone_authorization`](#clone_authorization) for which access to `sign_message` was requested
- `payloads`: one or more base64-encoded message payloads to sign
- `return_signed_payloads`: (optional) if present, a boolean value indicating whether the result should contain a `signed_payloads` entry. If not present, defaults to `false`.

###### Result
{: .no_toc }

```
{
    "signatures": ["<signature>", ...],
    “signed_payloads”: [“<signed_message>”, ...],
}
```

where:

- `signatures`: base64-encoded message signatures
- `signed_payloads`: (optional) base64-encoded signed message payloads. This will be present only if `return_signed_payloads` was present and `true` in params.

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_REAUTHORIZE` if auth_token requires [`reauthorization`](#reauthorize)
- `ERROR_AUTHORIZATION_FAILED` if the auth_token is invalid, or not authorized for `sign_message`
- `ERROR_INVALID_PAYLOAD`

  ```
  “data”: {
      “valid”: [<message_valid>, ...],
  }
  ```

  if any message does not represent a valid message for signing, where:

  - `message_valid`: an array of booleans with the same length as `payloads` indicating which are valid
- `ERROR_NOT_SIGNED` if the wallet endpoint declined to sign these messages for any reason

##### Description

The wallet endpoint should present the provided messages for approval. If approved, the wallet endpoint should sign the messages with the private key for the authorized account, and return the signed messages to the dapp endpoint.

### Constants

The protocol defines the following constants:

```
const ERROR_REAUTHORIZE = -1
const ERROR_AUTHORIZATION_FAILED = -2
const ERROR_INVALID_PAYLOAD = -3
const ERROR_NOT_SIGNED = -4
const ERROR_NOT_COMMITTED = -5

const ERROR_ATTEST_ORIGIN_ANDROID = -100

const MAX_TRANSACTION_SZ = 1232
```

### Illustrative diagrams

#### Session establishment

{% plantuml %}
{% include_relative _diagrams/association.plantuml %}
{% endplantuml %}

#### Encrypted message wrapping

{% plantuml %}
{% include_relative _diagrams/encrypted_message_wrapping.plantuml %}
{% endplantuml %}

#### Authorize and sign transaction

{% plantuml %}
{% include_relative _diagrams/authorize_and_sign.plantuml %}
{% endplantuml %}

#### Reauthorize and sign transaction

{% plantuml %}
{% include_relative _diagrams/reauthorize_and_sign.plantuml %}
{% endplantuml %}

## Dapp identity verification

Dapp endpoint identity verification is domain-based - through various platform-specific mechanisms, a dapp endpoint attests to the wallet that it is associated with a specific web domain. Wallet endpoints are responsible for deciding whether to extend trust to each dapp based on the attested web domain.

### Android

#### Native dapp

Identity verification on Android relies on [Digital Asset Links](https://developers.google.com/digital-asset-links/v1/statements) to associate apps with a web domain. Both native wallet apps and native dapps on Android should:

- Be identified by package name and signing key fingerprint in a `/.well-known/assetlinks.json` file hosted on the web domain of the app developer
- Use [App Links](https://developer.android.com/training/app-links) for all Activity `<intent-filter>` elements in the app manifest that accept the web domain, (or any subdomain)

Failure to establish these Digital Asset Links will result in the inability of wallet endpoints to verify the identity of dapp endpoints, and puts the users’ funds at risk from malicious dapps.

Native dapp endpoints are required to associate with the wallet endpoint using either a [Local URI](#local-uri) or an [Endpoint-specific URI](#endpoint-specific-uris), started via an Intent with [`startActivityForResult`](https://developer.android.com/reference/android/app/Activity#startActivityForResult(android.content.Intent,%20int)). This allows the wallet endpoint to retrieve the calling package identity, with [`getCallingPackage`](https://developer.android.com/reference/android/app/Activity#getCallingPackage()).

To verify the authenticity of the identity provided to [`authorize`](#authorize), the wallet endpoint should check the Digital Asset Link for the `identity` element URI and ensure that the calling package is signed with a certificate listed in an `android_app` statement within the Digital Asset Link file.

If the `identity` element does not contain a URI, or if the dapp endpoint’s calling package cannot be verified against an `android_app` target in the Digital Asset Link file, it is recommended that wallet endpoints decline to issue an authorization token to the dapp endpoint and return `ERROR_AUTHORIZATION_FAILED`.

#### Web dapp

Chromium (and related browsers, such as Chrome) associate with the wallet endpoint using either a [Local URI](#local-uri) or an [Endpoint-specific URI](#endpoint-specific-uris), started via an Intent with [`startActivity`](https://developer.android.com/reference/android/app/Activity#startActivity(android.content.Intent)), which does not provide the calling identity. In addition, [`EXTRA_REFERRER`](https://developer.android.com/reference/android/content/Intent?hl=en#EXTRA_REFERRER) may not always be provided in the Intent, and even if it is, it is not a secure source of identity on Android. To provide dapp endpoint identity verification, a different method is required.

The browser security sandbox can attest to the origin of a web dapp endpoint, but only within the confines of the sandbox. To extend trust from the browser to the wallet endpoint, it must be brought into the browser security model. [Trusted Web Activities](https://developer.chrome.com/docs/android/trusted-web-activity/) enable this by allowing a verifiable `postMessage` channel to be established between the native wallet app and the web browser (once again, using [Digital Asset Links](https://developers.google.com/digital-asset-links/v1/statements)). Once a channel is established, the wallet app can request that a trusted script running within the browser attest to the identity of the dapp.

The wallet endpoint’s software developer must host an HTML document containing an attestation script on a web domain whose Digital Asset Link file contains an `android_app` target verifying the wallet endpoint app. This attestation script is responsible for:

1. Generating an attestation keypair for the wallet endpoint, and issuing the public key to it
2. Attesting to the origin of dapp endpoints, by signing an attestation payload with the requested private key

The contents of this script are an implementation detail of wallet endpoints.

On an [`authorize`](#authorize), [`reauthorize`](#reauthorize), or [`clone_authorization`](#clone_authorization) attempt from a local web dapp endpoint, the wallet endpoint should check if the provided identity has been authorized during this session. If not, it should return `ERROR_ATTEST_ORIGIN_ANDROID` with an identity challenge. The dapp endpoint should load the identity attestation script and use postMessage to convey the dapp endpoint’s origin and the wallet endpoint challenge. The attestation script should construct a response to the challenge containing the origin, sign it with the corresponding wallet attestation private key, and return it to the dapp endpoint. The dapp endpoint should reattempt the failed authorization request, including the attestation response.

##### authorize, reauthorize, and clone_authorization modifications

###### Method
{: .no_toc }

```
authorize, reauthorize, clone_authorization
```

###### Params
{: .no_toc }

```
{
    ...,
    “attest_origin”: “<attest_origin_token>”,
}
```

where:

- `attest_origin_token`: a token, generated by `attest_origin_uri`, from which the wallet endpoint can verify the authenticity of the `identity` parameter to this method

###### Result
{: .no_toc }

No changes

###### Errors
{: .no_toc }

```
...
ERROR_ATTEST_ORIGIN_ANDROID
“data”: {
    “context”: “<context>”,
    “challenge”: “<challenge>”,
    “attest_origin_uri”: “<uri>”,
}
```

if the `identity` provided in params requires verification, where:

- `context`: the attestation context (for e.g., a key ID)
- `challenge`: a base64-encoded challenge nonce uniquely identifying this attestation request
- `uri`: the URI of the wallet endpoint HTML document containing the attestation script

On receipt of an `ERROR_ATTEST_ORIGIN_ANDROID` response, the dapp endpoint should decode the challenge nonce, construct the string `“attest-origin” || decoded_challenge || session_secret`, calculate the SHA256 hash of this string, and base64-encode the result. This value, `h`, binds `challenge` to this dapp endpoint (by including the session secret). It should then construct the JSON message:

```
{
    “m: “origin-attest”,
    “h”: <h>,
    “context”: <context>,
}
```

The dapp endpoint should load `origin_attest_uri` into an invisible iframe, and `postMessage(origin_attest_msg)` to it. The dapp endpoint should then await a response message from the iframe (via a message event listener). On receipt, the dapp endpoint should destroy the iframe, and reattempt the authorization request to the wallet endpoint, including the returned message contents as the `attest_origin` parameter.

##### Non-normative commentary

Many of the details of this identity verification process are left as an implementation detail of the wallet endpoint. However, as an illustrative example, here is one scheme which could be used to implement identity verification.

{% plantuml %}
{% include_relative _diagrams/android_origin_attestation.plantuml %}
{% endplantuml %}

This approach uses a Custom Tab (possibly in Trusted Web Activity mode) with the same browser backend as the association request to securely create the attestation keypair, which is stored in local storage of the wallet endpoints web domain (from which the origin attestation script is fetched). The public keypoint is returned and stored by the wallet.

When the wallet determines that an origin attestation is necessary, it returns this same origin attestation script URI to the dapp endpoint, along with the challenge parameters. The dapp endpoint constructs the appropriate origin attestation message, opens the attestation script in an iframe, and sends it the message using postMessage. This provides the dapp endpoint origin to the attestation script, signed with the private keypoint. This message is returned to the dapp endpoint, which then reattempts the authorization request with the message as the attest_origin parameter. The wallet endpoint is able to use the corresponding public keypoint to validate the authenticity of this message, and then subsequently validate the dapp endpoint identity using the token payload.

### iOS

__TODO__

### Remote

No dapp endpoint verification method is defined by this version of the mobile-wallet-adapter protocol. Wallet endpoints are free to perform whatever implementation-specific verification techniques they desire (including rejecting all remote dapp endpoint authorization requests as unverified).

If a wallet endpoint supports the [`clone_authorization`](#clone_authorization) method, at a minimum it should also support [`reauthorize`](#reauthorize) on the resulting auth token from a remote dapp endpoint.

## Reflector protocol

Reflection is an extremely simple protocol; it will reflect all communications received from each endpoint to the other endpoint, up to a maximum size of 4KB per frame.

The reflector should listen for WebSocket secure connections to:

```
wss://<host_authority>/reflect?id=<reflector_unique_id>
```

The reflector will maintain two data sets:

1. Half open reflections - this set contains endpoint connections which are waiting for their corresponding counterparty to connect, along with the connection established time
1. Fully open reflections - this set contains endpoint connections for which the corresponding counterparty has connected, along with the reflection established time

On a new connection, the reflector will take the following action:

- If there is an entry in the fully open reflections data set for the specified `reflector_unique_id`, the connection will be closed immediately
- If there is an entry in the half open reflections data set for the specified `reflector_unique_id`, that entry will be removed and a new entry added to the fully open reflections data set for the connection pair. Reflection will be started for this connection pair.
- Otherwise, an entry will be added to the half open reflections data set for this connection. All incoming data on this connection will be silently discarded.

When reflection begins, the reflector will send an [`APP_PING`](#app_ping) message to each connection, and then begin transmitting all messages received from each connection to the other connection in the pair.

On a disconnection:

- If the connection is part of the fully open reflections data set, the entry will be removed and the other connection closed as well
- Otherwise, the entry for the connection will be removed from the half-open reflections data set

Entries in the half open data set should be removed, and the connection closed, if still present in this set  X seconds after being added. Entries in the fully open data set should be removed, and both connections closed, if still present in this list X seconds after being added.

To ensure that all active connections are maintained, the reflector shall ensure that periodic [`PING`](https://datatracker.ietf.org/doc/html/rfc6455#section-5.5.2) frames are sent to each connection.
