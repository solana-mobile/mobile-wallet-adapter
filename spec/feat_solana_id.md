---
layout: default
title: Mobile Wallet Adapter Solana ID feature extension specification
---

# Solana Id feature specification

1. TOC
{: toc}

# Version

This specification uses [semantic versioning](https://en.wikipedia.org/wiki/Software_versioning#Semantic_versioning)

## Changelog (oldest to newest)

| Version | Description                                                                                                                         |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| x.x.x   | {version description}                                                                                                               |

# Non-normative front matter

The contents of this section attempt to explain this feature extension with context and goals, but do not form a formal part of the specification itself.

TODO

## Summary

Solana ID is a combination of user account attestation and verified device attestation. 

## User stories

These user stories outline the goals and user experiences that this feature aims to enable.

1. As a dapp developer, I want to allow users to sign in with a Solana account from a verified device so that I can uniquely idenfiy these users and provide valuable user experiences. 
1. As a dapp developer, I want to verify that a user owns a provided Solana account, and verify that the account is tied to a verified device so that I can ensure the user is genuine.
1. As a user, I want to be able to login into web3 applications with a clear understanding of which account I am signing in with so that I can interact with these applications.

## Requirements

These requirements are derived from the user stories, and exist to guide feature specification design decisions.

1. Provide user account attestation. 

   _Rationale: Dapps will need to verify that a user controls a provided account (public key)._

1. Provide device attestation. 

   _Rationale: Dapps will need to verify that a user is logging in from, or in possesion of a verified device._

1. Provide a user experience similar to [Sign in With Solana](). 

   _Rationale: Users shuold be presented with a friendly UI that clearly shows the app or domain that is requesting the login, and which account is being used to sign in._

# Specification

This specification is an extension of the [Mobile Wallet Adapter Specification](spec.md). All existing terminology and functionality is inherited by this extension unless explicitly noted.

## Feature Identifier

The namespace for this feature is `{solana}` and the name (reference) of the feature is `{solanaId}`. The resulting feature identifier for this feature is `solana:solanaId`.

## Wallet RPC interface

### Extensions to Existing RPC Methods

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
    "chain": "<chain>",
    "features": ["<feature_id>", ...],
    "addresses": ["<address>", ...],
    “auth_token”: “<auth_token>”,
    "cluster": "<cluster>",
    "solana_id_payload": <solana_id_payload>,
}
```

where:

- `solana_id_payload`: (optional) a value object containing following:
  - `device_attestation_challenge`: a 32 byte string
  - the Sign-In input fields as described by the [Sign In With Solana specification](https://github.com/phantom/sign-in-with-solana?tab=readme-ov-file#sign-in-input-fields).

The remaining method parameters are unchanged from the base [`autorize`](#spec.md#authorize) method.

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
    "sign_in_result": {
        “address”: “<address>", 
        "signed_message": "<signed_message>"
        "signature": "<signature>"
        "signature_type": "<signature_type>"
        "device_attestation_result": <device_attestation_result>
    }
}
```

where:

- `sign_in_result`: (optional) if the authorize request included a [Sign In With Solana](https://siws.web3auth.io/spec) sign in payload, the result must be returned here as a value object containing the following:
  - `address`: the address of the account that was signed in. The address of the account may be different from the provided input address, but must be the address of one of the accounts returned in the `accounts` field. 
  - `signed_message`: the base64-encoded signed message payload
  - `signature`: the base64-encoded signature
  - `signature_type`: (optional) the type of the message signature produced. If not provided in this response, the signature must be `"ed25519"`.
  - `device_attestation_result`: TODO: X.509 certificate? how will this be structured? 
  
The remaining result parameters are unchanged from the base [`autorize`](#spec.md#authorize) method.

###### Errors
{: .no_toc }

Error responses are unchanged from the base [`autorize`](#spec.md#authorize) method.

##### Description

TODO

##### Non-normative commentary
