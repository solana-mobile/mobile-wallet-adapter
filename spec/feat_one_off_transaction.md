---
layout: default
title: Mobile Wallet Adapter One-Shot Sessions feature extension specification
---

# One-Off Transactions feature specification

1. TOC
{: toc}

# Version

This specification uses [semantic versioning](https://en.wikipedia.org/wiki/Software_versioning#Semantic_versioning)

## Changelog (oldest to newest)

| Version | Description                                                                                                                         |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| x.x.x   | {version description}                                                                                                               |

# Non-normative front matter

Often times, a dapp needs to send a single request to a wallet for approval. With a standard MWA session, a user will be presented with 2 steps in order to complete a single request. The dapp must first call `authorize` to establish an authorized session, then send its request for approval. This feature allows a dapp to execute a single request to a wallet endpoint and the wallet will show a single UI modal to the user.  

## Summary

One-Shot sessions allow a dapp end point to access a single priviledgd method in a single UI modal. 

## User stories

These user stories outline the goals and user experiences that this feature aims to enable.

1. As a user, I want to be able to sign transactions with fewer steps and interactions so that I can interact with dapps in a seamless and less intrusive flow.
2. As a dapp developer, I want the ability to send a one off request to wallet endpoints with a single interaction form the the user so that I can more easily send signing requests that do not require subsequent requests. 

## Requirements

These requirements are derived from the user stories, and exist to guide feature specification design decisions.

1. One shot requests should use the existing authorization and identity verification mechanisms present in the mobile wallet adapter specification.

   _Rationale: This ensures that wallets can service these one-off requests the same security guarantees present in a standard MWA flow._

# Specification

This specification is an extension of the [Mobile Wallet Adapter Specification](spec.md). All existing terminology and functionality is inherited by this extension unless explicitly noted.

## Feature Identifier

The namespace for this feature is `solana` and the name (reference) of the feature is `authorizeOneShot`. The resulting feature identifier for this feature is `solana:authorizeOneShot`.

## Wallet RPC interface

### Extensions to Existing RPC Methods

A detailed description of any existing RPC methods that are extended by this feature, including added input parameters, result payloads, and possible new errors returned by each method. 

### New RPC Methods

A detailed description of any new RPC methods specified by this feature extension, including input parameters, result payloads, and possible errors returned by each method. 

### Non-privileged methods

Non-privileged methods do not require the current session to be in an authorized state to invoke them (though they may still accept an `auth_token` to provide their functionality).

#### authorize_oneshot

##### JSON-RPC method specification

###### Method 
{: .no_toc }

```
authorize_oneshot
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
    "chain": "<chain>",
    "features": ["<feature_id>", ...],
    "addresses": ["<address>", ...],
    "cluster": "<cluster>"
}
```

where:

- `identity`: a JSON object, containing:
  - `uri`: (optional) a URI representing the web address associated with the dapp endpoint making this authorization request. If present, it must be an absolute, hierarchical URI.
  - `icon`: (optional) either a data URI containing a base64-encoded SVG, WebP, PNG, or GIF image or a relative path (from `uri`) to an image asset file of an icon identifying the dapp endpoint making this authorization request
  - `name`: (optional) the display name for this dapp endpoint
- `chain`: (optional) if set, the [chain identifier](#chain-identifiers) for the chain with which the dapp endpoint intends to interact; supported values include `solana:mainnet`, `solana:testnet`, `solana:devnet`, `mainnet-beta`, `testnet`, `devnet`. If not set, defaults to `solana:mainnet`.
- `addresses`: (optional) if set, a list of base64 encoded account addresses that the dapp endpoint wishes to be included in the authorized scope. Defaults to `null`. 
- `features`: (optional) if set, a list of [feature identifiers](#feature-identifiers) that the dapp endpoint intends to use in the session. Defaults to `null`. 
- `cluster`: (optional) an alias for `chain`. This parameter is maintained for backwards compatibility with previous versions of the spec, and will be ignored if the `chain` parameter is present. 

###### Result
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
    “accounts”: [
        {
            “address”: “<address>", 
            "display_address": "<display_address>",
            "display_address_format": "<display_address_format>",
            “label”: “<label>”, 
            "icon": "<icon>",
            "chains": ["<chain_id>", ...], 
            "features": ["<feature_id>", ...]
        },
        ...
    ],
    “wallet_uri_base”: “<wallet_uri_base>”,
}
```

where:

- `auth_token`: an opaque string representing a unique identifying token issued by the wallet endpoint to the dapp endpoint. The format and contents are an implementation detail of the wallet endpoint. The dapp endpoint can use this on future connections to reauthorize access to [privileged methods](#privileged-methods).
- `accounts`: one or more value objects that represent the accounts to which this auth token corresponds. These objects hold the following properties:
  - `address`: a base64-encoded public key for this account. 
  - `display_address`: (optional) the address for this account. The format of this string will depend on the chain, and is specified by the `display_address_format` field
  - `display_address_format`: (optional) the format of the `display_address`.
  - `chains`: a list of [chain identifiers](#chain-identifiers) supported by this account. These should be a subset of the chains supported by the wallet.
  - `features`: (optional) a list of [feature identifiers](#feature-identifiers) that represent the features that are supported by this account. These features must be a subset of the features returned by [`get_capabilities`](#get_capabilities). If this parameter is not present the account has access to all available features (both mandatory and optional) supported by the wallet.  
  - `label`: (optional) a human-readable string that describes the account. Wallet endpoints that allow their users to label their accounts may choose to return those labels here to enhance the user experience at the dapp endpoint.
  - `icon`: (optional) a data URI containing a base64-encoded SVG, WebP, PNG, or GIF image of an icon for the account. This may be displayed by the app.
- `wallet_uri_base`: (optional) if this wallet endpoint has an [endpoint-specific URI](#endpoint-specific-uris) that the dapp endpoint should use for subsequent connections, this member will be included in the result object. The dapp endpoint should use this URI for all subsequent connections where it expects to use this `auth_token`.

###### Errors
{: .no_toc }

- `-32601` (Method not found) if `authorize_oneshot` is not supported by this wallet endpoint
- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_AUTHORIZATION_FAILED` if the wallet endpoint did not authorize access to the requested privileged methods
- `ERROR_CHAIN_NOT_SUPPORTED` if the wallet endpoint does not support the requested chain

##### Description

This method allows the dapp endpoint to access single privileged method from a wallet endpoint while requiring only one interaction from the user. Wallets supporting this feature will cache the request and await a single privileged request from the dapp. Upon receipt of the subsequent request, only then will the wallet present UI to the user to confirm. Any further requests sent within this authorization scope will be rejected by the wallet endpoint. 

The parameters of this method are identical to the standard [`authorize`](spec.md#authorize) request and should be handled in the same manor. Wallet endpoints should still perform the same identiy verification that is performed for a standard [`authorize`](spec.md#authorize) request:

>Wallet endpoints should make every effort possible to [verify the authenticity](spec.md#dapp-identity-verification) of the presented identity. While the `uri` parameter is optional, it is strongly recommended - without it, the wallet endpoint may not be able to verify the authenticity of the dapp.

###### Non-normative commentary

This method is optional, to support immediate completion of a single privileged method request with fewer steps for a better user experience for dapps that do not require multiple requests to a wallet.  
