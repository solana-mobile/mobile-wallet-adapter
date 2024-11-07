---
layout: default
title: Mobile Wallet Adapter 1.0 specification
---

{%- comment -%}
Please don't introduce unnecessary line breaks in this specification - it's diff-unfriendly.
{%- endcomment -%}

# Mobile Wallet Adapter specification

1. TOC
{: toc}

# Version

This specification uses [semantic versioning](https://en.wikipedia.org/wiki/Software_versioning#Semantic_versioning)

**Version: 1.0.0**

## Changelog (oldest to newest)

| Version | Description                                                                                                                         |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| 1.0.0   | Initial release version of the Mobile Wallet Adapter specification (identical to pre-release version 0.9.1)                         |

### Pre-v1.0.0 changelog

_This is retained for historical reference only. None of these versions were official releases, and there are no guarantees of backward compatibility._

| Version | Description                                                                                                                         |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| 0.1.0   | Initial draft                                                                                                                       |
| 0.2.0   | Updates based on wallet adapter feedback                                                                                            |
| 0.2.1   | Fix a few missed pluralizations                                                                                                     |
| 0.3.0   | Sessions now track authorization statefully, rather than by providing `auth_token` to each [privileged method](#privileged-methods) |
| 0.3.1   | Enforce HTTPS for endpoint-specific URIs                                                                                            |
| 0.3.2   | Replace timeout placeholders with minimum timeouts                                                                                  |
| 0.3.3   | `sign_messages` should take multiple addresses for signing (for parity with `sign_transactions` behavior)                           |
| 0.9.0   | Advancing spec version to 0.9.0 (near-final version)                                                                                |
| 0.9.1   | Integrate some review feedback                                                                                                      |

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

If the WebSocket transport is unavailable locally after no less than 30 seconds, the dapp endpoint should display user guidance (e.g. download a wallet) and optionally present the opportunity to connect to a remote wallet endpoint using one or more of the other association mechanisms.

#### Android

If a wallet endpoint is installed which has registered an Activity for this URI scheme and format, it will be launched. Upon launch via this URI, the wallet endpoint should start a WebSocket server on port `port_number` and begin listening for connections to `/solana-wallet` for no less than 10 seconds. This websocket server should only accept connections from the localhost.

Whether launched from a web browser or a native dapp endpoint, the Intent’s action will be [`android.intent.action.VIEW`](https://developer.android.com/reference/android/content/Intent#ACTION_VIEW) and the category will be [`android.intent.category.BROWSABLE`](https://developer.android.com/reference/android/content/Intent#CATEGORY_BROWSABLE). When launched by a web browser, no caller identity will be available, and as such, the referrer details available within the Intent cannot be used to verify the origin of the association. When launched by a native dapp endpoint, this Intent should be sent with [`startActivityForResult`](https://developer.android.com/reference/android/app/Activity#startActivityForResult(android.content.Intent,%20int)), allowing the wallet endpoint to query the caller identity. The result returned to the calling dapp endpoint is not specified.

#### iOS

_iOS support is planned for a future version of this specification_

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
- `reflector_unique_id` is a number generated securely at random by the dapp endpoint, 0 ≤ n ≤ 2^53 - 1

This URI should be provided to the wallet endpoint through an out-of-band mechanism, detailed in the subsections below. Each of the dapp and wallet endpoints should attempt to connect to the WebSocket address `wss://<host_authority>/reflect?id=<reflector_unique_id>`. On connection, each endpoint should wait for the [Reflector protocol](#reflector-protocol) to signal that the counterparty endpoint has connected.

The dapp endpoint must wait no less than 30 seconds for reflection to commence. The wallet endpoint must wait no less than 10 seconds for reflection to commence. If it does not commence, the endpoints will disconnect and present appropriate error messages to the user.

#### QR codes

Dapp endpoints must support displaying the remote URI to the user encoded as a QR code. After displaying a QR code, the dapp endpoint should connect to the specified reflector. Wallet endpoints on devices with a camera should support scanning QR codes within the app, receiving notifications from the system that a QR code encoding a remote URI has been received, or both. Upon receipt of a remote URI from a scanned QR code, the wallet endpoint should attempt to connect to the specified reflector.

#### Clipboard

Dapp endpoints may optionally also support copying the remote URI to the system clipboard. After copying the remote URI to the clipboard, the dapp endpoint should connect to the specified reflector. Wallet endpoints on desktop OSes should provide a method to accept a remote URI from the system clipboard. Upon receipt of a remote URI from the system clipboard, the wallet endpoint should attempt to connect to the specified reflector.

### Endpoint-specific URIs

During [Session Establishment](#session-establishment), the wallet endpoint may return a URI prefix to use for future association attempts. This is expected to be used with [App Links](https://developer.android.com/training/app-links) or [Universal Links](https://developer.apple.com/ios/universal-links/), to ensure that the desired wallet app is launched by the dapp. A dapp should reject URI prefixes with schemes other than `https:` for security reasons. If a dapp has been informed of a URI prefix for a wallet, it should use it with the same path elements and parameters provided as for the `solana-wallet:` URI scheme. For e.g., if an Android wallet endpoint handles App Links for solanaexamplewallet.io, it could provide a prefix of:

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

If qd signature verification fails, if no `HELLO_REQ` message is received by the wallet endpoint within no less than 10 seconds, or if a second `HELLO_REQ` message is received by the wallet endpoint at any time during the connection, all ephemeral key materials should be discarded, and the connection should be closed.

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

If either public keypoint `Qd` or `Qw` is not valid, if no `HELLO_RSP` message is received by the dapp endpoint within no less than 10 seconds, or if a second `HELLO_RSP` message is received by the dapp endpoint at any time during the connection, all ephemeral key materials should be discarded, and the connection should be closed.

## Wallet RPC interface

### Operation

After [session establishment](#session-establishment) completes, the wallet endpoint is ready to accept [JSON-RPC 2.0](https://www.jsonrpc.org/specification) non-privileged method calls from the dapp endpoint. To invoke privileged methods, a dapp endpoint must first put the session into an authorized state via either an [`authorize`](#authorize) or a [`reauthorize`](#reauthorize) method call. For details on how a session enters and exits an authorized state, see the [non-privileged methods](#non-privileged-methods).

### Encrypted message wrapping

After the [session establishment](#session-establishment) process completes, every message received by an endpoint is expected to be encrypted with AES-128-GCM (as specified by [NIST SP 800-38D](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf)). The sending endpoint should prepare the encrypted message by concatenating:

- the message sequence number, a 4-byte big-endian unsigned integer
- a random 12-byte IV (which should be newly generated for each encrypted message)
- the AES-128-GCM message ciphertext, encrypted using the private key created during [session establishment](#session-establishment) and the random IV, and including the 4-byte message sequence number as additional authorized data (AAD)
- the 16-byte authentication tag generated during AES-128-GCM encryption

After decrypting the ciphertext with the shared secret generated during [session establishment](#session-establishment) and verifying the authentication tag, the receiving endpoint should further interpret it as a [JSON-RPC 2.0](https://www.jsonrpc.org/specification) message.

The message sequence number is monotonically increasing, and starts at 1 when session establishment completes. Each endpoint maintains its own independent sequence number, and increments it by 1 each time an encrypted message is created and sent. On receipt of an encrypted message, each endpoint should verify that the sequence number is 1 greater than that of the previous message received (other than for the first message received). On receipt of a message with a sequence number set to anything other than the expected next value, the encrypted message should be discarded and the connection closed.

#### Non-normative commentary

Why does the protocol specify this, rather than rely on, e.g., TLS?

- In remote usage, a reflector server is used to mediate a connection between a dapp and wallet endpoint. This reflector is viewed as an adversary, and so should not have access to the plaintext of endpoint communications.
- This protocol supports multiple transports (i.e. `ws:` for local connections, `wss:` for reflector connections, Bluetooth LE for wireless connections). Each of these has different confidentiality and authenticity guarantees. By encrypting messages at the application layer, the protocol can provide a uniform minimum security guarantee for a heterogeneous set of transports.

### Non-privileged methods

Non-privileged methods do not require the current session to be in an authorized state to invoke them (though they may still accept an `auth_token` to provide their functionality).

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
    "cluster": "<cluster>",
}
```

where:

- `identity`: a JSON object, containing:
  - `uri`: (optional) a URI representing the web address associated with the dapp endpoint making this authorization request. If present, it must be an absolute, hierarchical URI.
  - `icon`: (optional) a relative path (from `uri`) to an image asset file of an icon identifying the dapp endpoint making this authorization request
  - `name`: (optional) the display name for this dapp endpoint
- `cluster`: (optional) if set, the Solana network cluster with which the dapp endpoint intends to interact; supported values include `mainnet-beta`, `testnet`, `devnet`. If not set, defaults to `mainnet-beta`.

###### Result
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
    “accounts”: [
        {“address”: “<address>", “label”: “<label>”},
        ...
    ],
    “wallet_uri_base”: “<wallet_uri_base>”,
}
```

where:

- `auth_token`: an opaque string representing a unique identifying token issued by the wallet endpoint to the dapp endpoint. The format and contents are an implementation detail of the wallet endpoint. The dapp endpoint can use this on future connections to `reauthorize` access to [privileged methods](#privileged-methods).
- `accounts`: one or more value objects that represent the accounts to which this auth token corresponds. These objects hold the following properties:
  - `address`: a base64-encoded address for this account
  - `label`: (optional) a human-readable string that describes the account. Wallet endpoints that allow their users to label their accounts may choose to return those labels here to enhance the user experience at the dapp endpoint.
- `wallet_uri_base`: (optional) if this wallet endpoint has an [endpoint-specific URI](#endpoint-specific-uris) that the dapp endpoint should use for subsequent connections, this member will be included in the result object. The dapp endpoint should use this URI for all subsequent connections where it expects to use this `auth_token`.

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_AUTHORIZATION_FAILED` if the wallet endpoint did not authorize access to the requested privileged methods
- `ERROR_CLUSTER_NOT_SUPPORTED` if the wallet endpoint does not support the requested Solana cluster

##### Description

This method allows the dapp endpoint to request authorization from the wallet endpoint for access to [privileged methods](#privileged-methods). On success, it returns an `auth_token` providing access to privileged methods, along with addresses and optional labels for all authorized accounts. It may also return a URI suitable for future use as an [endpoint-specific URI](#endpoint-specific-uris). After a successful call to `authorize`, the current session will be placed into an authorized state, with privileges associated with the returned `auth_token`. On failure, the current session with be placed into the unauthorized state.

The returned `auth_token` is an opaque string with meaning only to the wallet endpoint which created it. It is recommended that the wallet endpoint include a mechanism to authenticate the contents of auth tokens it issues (for e.g., with an HMAC, or by encryption with a secret symmetric key). This `auth_token` may be used to [`reauthorize`](#reauthorize) future sessions between these dapp and wallet endpoints.

Dapp endpoints should make every effort possible to [verify the authenticity](#dapp-identity-verification) of the presented identity. While the `uri` parameter is optional, it is strongly recommended - without it, the wallet endpoint may not be able to verify the authenticity of the dapp.

The `cluster` parameter allows the dapp endpoint to select a specific Solana cluster with which to interact. This is relevant for both [`sign_transactions`](#sign_transactions), where a wallet may refuse to sign transactions without a currently valid blockhash, and for [`sign_and_send_transactions`](#sign_and_send_transactions), where the wallet endpoint must know which cluster to submit the transactions to. This parameter would normally be used to select a cluster other than `mainnet-beta` for dapp development and testing purposes. Under normal circumstances, this field should be omitted, in which case the wallet endpoint will interact with the `mainnet-beta` cluster.

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

If, during the current session, the specified auth token was returned by the most recent call to [`authorize`](#authorize) or [`reauthorize`](#reauthorize), the session with be placed into the unauthorized state.

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

- `identity`: as defined for [`authorize`](#authorize)
- `auth_token`: an opaque string previously returned by a call to [`authorize`](#authorize), [`reauthorize`](#reauthorize), or [`clone_authorization`](#clone_authorization)

###### Result
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
    “accounts”: [
        {“address”: “<address>", “label”: “<label>”},
        ...
    ],
    “wallet_uri_base”: “<wallet_uri_base>”,
}
```

where:

- `auth_token`: as defined for [`authorize`](#authorize)
- `accounts`: as defined for [`authorize`](#authorize)
- `wallet_uri_base`: as defined for [`authorize`](#authorize)

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_AUTHORIZATION_FAILED` if the wallet endpoint declined to authorize the current session with `auth_token` for any reason

##### Description

This method attempts to put the current session in an authorized state, with privileges associated with the specified `auth_token`.

On success, the current session will be placed into an authorized state. Additionally, updated values for `auth_token`, `accounts`, and/or `wallet_uri_base` will be returned. These may differ from those originally provided in the [`authorize`](#authorize) response for this auth token; if so, they override any previous values for these parameters. The prior values should be discarded and not reused. This allows a wallet endpoint to update the auth token used by the dapp endpoint, or to modify the set of authorized account addresses or their labels without requiring the dapp endpoint to restart the authorization process.

If the result is `ERROR_AUTHORIZATION_FAILED`, this auth token cannot be reused, and should be discarded. The dapp endpoint should request a new token with the [`authorize`](#authorize) method. The session with be placed into the unauthorized state.

##### Non-normative commentary

`reauthorize` is intended to be a lightweight operation, as compared to [`authorize`](#authorize). It normally should not present any UI to the user, but instead perform only the subset of dapp endpoint identity checks that can be performed quickly and without user intervention. The intent is to quickly verify that an auth token remains valid for use by this dapp endpoint. If verification fails for any reason, the wallet endpoint should report `ERROR_AUTHORIZATION_FAILED` to the dapp endpoint, which would then discard the stored auth token and begin authorization from scratch with a new call to [`authorize`](#authorize).

Wallet endpoints should balance the responsibility for performing these identity checks against their latency impact. For example, a wallet endpoint may choose to accept an auth token issued in the last several minutes without performing any additional checks, but require dapp endpoint identity checks to be performed for an auth token that was issued before then. This facilitates multiple [sessions to be established](#session-establishment) in a short timespan, related to the same user interaction with a dapp endpoint. The auth token validity policy is a competency of the wallet endpoint, and dictating any specific auth token lifespan or timeout is outside the scope of this protocol specification. However, it is strongly recommended that wallet endpoints do not issue auth tokens with unlimited lifespans or for which dapp endpoint identity checks are never performed on `reauthorize`.

#### get_capabilities

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
get_capabilities
```

###### Params
{: .no_toc }

```
{}
```

###### Result
{: .no_toc }

```
{
    "supports_clone_authorization": <supports_clone_authorization>,
    "supports_sign_and_send_transactions": <supports_sign_and_send_transactions>,
    "max_transactions_per_request": <max_transactions_per_request>,
    "max_messages_per_request": <max_messages_per_request>,
    "supported_transaction_versions": [<supported_transaction_versions>, ...]
}
```

where:

- `supports_clone_authorization`: `true` if the [`clone_authorization`](#clone_authorization) method is supported, otherwise `false`
- `supports_sign_and_send_transactions`: `true` if the [`sign_and_send_transactions`](#sign_and_send_transactions) method is supported, otherwise `false`
- `max_transactions_per_request`: (optional) if present, the max number of transaction payloads which can be signed by a single [`sign_transactions`](#sign_transactions) or [`sign_and_send_transactions`](#sign_and_send_transactions) request. If absent, the implementation doesn't publish a specific limit for this parameter.
- `max_messages_per_request`: (optional) if present, the max number of transaction payloads which can be signed by a single [`sign_messages`](#sign_messages) request. If absent, the implementation doesn't publish a specific limit for this parameter.
- `supported_transaction_versions`: the Solana network transaction formats supported by this wallet endpoint. Allowed values are those defined for [`TransactionVersion`](https://solana-labs.github.io/solana-web3.js/v1.x/modules.html#TransactionVersion) (for e.g., `"legacy"`, `0`, etc).

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above

##### Description

This method can be used to enumerate the capabilities and limits of a wallet endpoint's implementation of this specification. It returns whether optional specification features are supported, as well as any implementation-specific limits.

### Privileged methods

Privileged methods require the current session to be in an authorized state to invoke them. For details on how a session enters and exits an authorized state, see the [non-privileged methods](#non-privileged-methods).

#### sign_transactions

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
sign_transactions
```

###### Params
{: .no_toc }

```
{
    “payloads”: [“<transaction>”, ...],
}
```

where:

- `payloads`: one or more base64-encoded transaction payloads to sign

###### Result
{: .no_toc }

```
{
    “signed_payloads”: [“<signed_transaction>”, ...],
}
```

where:

- `signed_payloads`: the corresponding base64-encoded signed transaction payloads

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_AUTHORIZATION_FAILED` if the current session is in the unauthorized state, either because [`authorize`](#authorize) or [`reauthorize`](#reauthorize) has not been invoked for the current session, or because the current session's authorization has been revoked by the wallet endpoint
- `ERROR_INVALID_PAYLOADS`

  ```
  “data”: {
      “valid”: [<transaction_valid>, ...],
  }
  ```

  if any transaction does not represent a valid transaction for signing, where:

    - `valid`: an array of booleans with the same length as payloads indicating which are valid

- `ERROR_NOT_SIGNED` if the wallet endpoint declined to sign these transactions for any reason
- `ERROR_TOO_MANY_PAYLOADS` if the wallet endpoint is unable to sign all transactions due to exceeding implementation limits. These limits may be available via [`get_capabilities`](#get_capabilities).

##### Description

The wallet endpoint should attempt to simulate the transactions provided by data and present them to the user for approval (if applicable). If approved (or if it does not require approval), the wallet endpoint should sign the transactions with the private keys for the requested authorized account addresses, and return the signed transactions to the dapp endpoint.

#### sign_and_send_transactions

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
sign_and_send_transactions
```

###### Params
{: .no_toc }

```
{
    “payloads”: [“<transaction>”, ...],
    "options": {
        “min_context_slot”: <min_context_slot>,
    }
}
```

where:

- `payloads`: one or more base64-encoded transaction payload to sign
- `options`: (optional) a JSON object, containing:
  - `min_context_slot`: (optional) if set, the minimum slot number at which to perform preflight transaction checks

###### Result
{: .no_toc }

```
{
    “signatures”: [“<transaction_signature>”, ...],
}
```

where:

- `signatures`: the corresponding base64-encoded transaction signatures

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `-32601` (Method not found) if `sign_and_send_transactions` is not supported by this wallet endpoint
- `ERROR_AUTHORIZATION_FAILED` if the current session is in the unauthorized state, either because [`authorize`](#authorize) or [`reauthorize`](#reauthorize) has not been invoked for the current session, or because the current session's authorization has been revoked by the wallet endpoint
- `ERROR_INVALID_PAYLOADS`

  ```
  “data”: {
      “valid”: [<transaction_valid>, ...]
  }
  ```

  if any transaction does not represent a valid transaction for signing, where:

  - `transaction_valid`: an array of booleans with the same length as `payloads` indicating which are valid
- `ERROR_NOT_SIGNED` if the wallet endpoint declined to sign this transaction for any reason
- `ERROR_NOT_SUBMITTED`

  ```
  “data”: {
      “signatures”: [“<transaction_signature>”, ...],
  }
  ```

  if the wallet endpoint was unable to submit one or more of the signed transactions to the network, where:

  - `signatures`: the corresponding base64-encoded transaction signatures for transactions which were successfully sent to the network, or `null` for transactions which were unable to be submitted to the network for any reason
- `ERROR_TOO_MANY_PAYLOADS` if the wallet endpoint is unable to sign all transactions due to exceeding implementation limits. These limits may be available via [`get_capabilities`](#get_capabilities).

##### Description

_Implementation of this method by a wallet endpoint is optional._

The wallet endpoint should attempt to simulate the transactions provided by `payloads` and present them to the user for approval (if applicable). If approved (or if it does not require approval), the wallet endpoint should verify the transactions, sign them with the private keys for the authorized addresses, submit them to the network, and return the transaction signatures to the dapp endpoint.

`options` allows customization of how the wallet endpoint processes the transactions it sends to the Solana network. If specified, `min_context_slot` specifies the minimum slot number that the transactions should be evaluated at. This allows the wallet endpoint to wait for its network RPC node to reach the same point in time as the node used by the dapp endpoint, ensuring that, e.g., the recent blockhash encoded in the transactions will be available.

###### Non-normative commentary

This method is optional, to support signing-only wallet endpoints which do not have any form of network connectivity.

it does not allow the dapp endpoint to specify the network RPC server to submit the transaction to; that is at the discretion of the wallet endpoint. If this is a detail that matters to the dapp endpoint, it should instead use the `sign_transactions` method and submit the transaction to a network RPC server of its choosing.

It is recommended that dapp endpoints verify that each transaction reached an appropriate level of commitment (typically either `confirmed` or `finalized`).

#### sign_messages

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
sign_messages
```

###### Params
{: .no_toc }

```
{
    "addresses": ["<address>", ...],
    “payloads”: [“<message>”, ...],
}
```

where:

- `addresses`: one or more base64-encoded addresses of the accounts which should be used to sign `message`. These should be a subset of the addresses returned by [`authorize`](#authorize) or [`reauthorize`](#reauthorize) for the current session's authorization.
- `payloads`: one or more base64url-encoded message payloads to sign

###### Result
{: .no_toc }

```
{
    “signed_payloads”: [“<signed_message>”, ...],
}
```

where:

- `signed_payloads`: the corresponding base64-encoded signed message payloads

###### Errors
{: .no_toc }

- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_AUTHORIZATION_FAILED` if the current session is in the unauthorized state, either because [`authorize`](#authorize) or [`reauthorize`](#reauthorize) has not been invoked for the current session, or because the current session's authorization has been revoked by the wallet endpoint
- `ERROR_INVALID_PAYLOADS`

  ```
  “data”: {
      “valid”: [<message_valid>, ...],
  }
  ```

  if any message does not represent a valid message for signing, where:

  - `message_valid`: an array of booleans with the same length as `payloads` indicating which are valid
- `ERROR_NOT_SIGNED` if the wallet endpoint declined to sign these messages for any reason
- `ERROR_TOO_MANY_PAYLOADS` if the wallet endpoint is unable to sign all messages due to exceeding implementation limits. These limits may be available via [`get_capabilities`](#get_capabilities).

##### Description

The wallet endpoint should present the provided messages for approval. If approved, the wallet endpoint should sign the messages with the private key for the authorized account address, and return the signed messages to the dapp endpoint. The signatures should be appended to the message, in the same order as `addresses`.

#### clone_authorization

##### JSON-RPC method specification

###### Method
{: .no_toc }

```
clone_authorization
```

###### Params
{: .no_toc }

```
{}
```

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
- `-32601` (Method not found) if [`clone_authorization`](#clone_authorization) is not supported by this wallet endpoint
- `ERROR_AUTHORIZATION_FAILED` if the current session is in the unauthorized state, either because [`authorize`](#authorize) or [`reauthorize`](#reauthorize) has not been invoked for the current session, or because the current session's authorization has been revoked by the wallet endpoint
- `ERROR_NOT_CLONED` if the wallet endpoint declined to clone the current authorization for any reason

##### Description

_Implementation of this method by a wallet endpoint is optional._

This method attempts to clone the session's currently active authorization in a form suitable for sharing with another instance of the dapp endpoint, possibly running on a different system. Whether or not the wallet endpoint supports cloning an `auth_token` is an implementation detail. If this method succeeds, it will return an `auth_token` appropriate for sharing with another instance of the same dapp endpoint.

###### Non-normative commentary

The `clone_authorization` method enables sharing of an authorization between related instances of a dapp endpoint (for example, running on a mobile device and a desktop OS). This is a sensitive operation; dapp endpoints must endeavor to transfer the token securely between dapp endpoint instances. The ability of wallet endpoints to validate the identity of the holder of the cloned token is an implementation detail, and may be weaker than that of the original token. As such, not all wallet endpoints are expected to support this feature.

### Constants

The protocol defines the following constants:

```
const ERROR_AUTHORIZATION_FAILED = -1
const ERROR_INVALID_PAYLOADS = -2
const ERROR_NOT_SIGNED = -3
const ERROR_NOT_SUBMITTED = -4
const ERROR_NOT_CLONED = -5
const ERROR_TOO_MANY_PAYLOADS = -6
const ERROR_CLUSTER_NOT_SUPPORTED = -7

const ERROR_ATTEST_ORIGIN_ANDROID = -100
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
{% include_relative _diagrams/1.0/authorize_and_sign.plantuml %}
{% endplantuml %}

#### Reauthorize and sign transaction

{% plantuml %}
{% include_relative _diagrams/1.0/reauthorize_and_sign.plantuml %}
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

_iOS support is planned for a future version of this specification_

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

Entries in the half open data set should be removed, and the connection closed, if still present in this set no less than 30 seconds after being added. Entries in the fully open data set should be removed, and both connections closed, if still present in this set no less than 90 seconds after being added. Entries in either data set may also be removed, and the connection(s) closed, before these time periods have elapsed at the discretion of the reflector (for e.g., during periods of limited resource availability).

To ensure that all active connections are maintained, the reflector shall ensure that periodic [`PING`](https://datatracker.ietf.org/doc/html/rfc6455#section-5.5.2) frames are sent to each connection.

# Support for other chains

While the initial version of this specification is designed to support the Solana network, future support for other chain types is envisioned. This could be accomplished with:

- modified association URIs (using schemes of the form `newchain-wallet://` instead of `solana-wallet://`)
- modified parameters to the RPC methods
  - `authorize` would have a different set of chain identifiers
  - `get_capabilities` would be used to express domain-specific capabilities relevant to the chain
  - `sign_and_send_transactions` would use chain-specific shapes for the `options` object

The specifics of the application of Mobile Wallet Adapter to each additional chain would be captured either with an update to this specification, or an extension accompanying this specification, as appropriate.