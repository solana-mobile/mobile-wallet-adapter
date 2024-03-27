---
layout: default
title: Mobile Wallet Adapter Ephemeral Accounts feature extension specification
---

# Ephemeral Accounts feature specification

1. TOC
{: toc}

# Version

This specification uses [semantic versioning](https://en.wikipedia.org/wiki/Software_versioning#Semantic_versioning)

## Changelog (oldest to newest)

| Version | Description                                                                                                                         |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| 0.1.0   | Initial draft                                                                                                                       |

# Non-normative front matter

The contents of this section attempt to explain this feature extension with context and goals, but do not form a formal part of the specification itself.

## Summary

The goal of this specification is to define an optional feature extension to the core MWA 2.0 protocol that enables mobile apps to request any number of signers from a wallet that are expected to sign transactions during the session and be discarded after. 

## User stories

These user stories outline the goals and user experiences that this protocol aims to enable.

1. As a user of a smart wallet or MPC wallet, I want my wallet to be compatible with all dapps, including those that use ephemeral signers in their transactions.
1. As a smart wallet or MPC wallet developer, I want dapps using ephemeral signers in their transactions to request the signers from the wallet so that the wallet can use these signers when the transaction is actually executed at some point in the future where the transaction blockhash may need to be updated and the transaction resigned by all signers. 

## Requirements

These requirements are derived from the user stories, and exist to guide specification design decisions.

1. A dapp will be able to request multiple ephemeral signers from a wallet.

   _Rationale: Multiple distinct signers may be needed by a dapp for a given transaction or session._
1. The dapp does not need to worry about how the signers are generated. How the ephemeral signers are generated is an implementation detail of the wallet. 

  _Rationale: The implementation of ephemeral signers can vary depending on the type of wallet being used._

# Specification

This specification is an extension of the [Mobile Wallet Adapter Specification](spec.md). All existing terminology and functionality is inherited by this extension unless explicitly noted.

## Feature Identifier

The namespace for this feature is `experimental` and the name (reference) of the feature is `ephemeralSigners`. The resulting feature identifier for this feature is `experimental:ephemeralSigners`.

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
    “auth_token”: “<auth_token>”,
    "sign_in_payload": <sign_in_payload>,
    "cluster": "<cluster>",
    "ephemeral_signers": <ephemeral_signers>
}
```

where:

- `ephemeral_signers`: (optional) a number indicating the number of ephemeral signers requested by the dapp endpoint.

All other request parameters remain unchanged from the [base specification `authorize` method](spec.md#authorize).

###### Result
{: .no_toc }

```
{
    “auth_token”: “<auth_token>”,
    “accounts”: [
        {
            “address”: “<address>", 
            “label”: “Ephemeral Signer 1”, 
            "chains": ["<chain_id>", ...], 
            "features": ["experimental:ephemeralSigners", ...]
        },
        ...
    ],
    “wallet_uri_base”: “<wallet_uri_base>”,
    "sign_in_result": {
        “address”: “<address>", 
        "signed_message": "<signed_message>"
        "signature": "<signature>"
        "signature_type": "<signature_type>"
    }
}
```

Ephemeral signer accounts **must** include the feature identifier `experimental:ephemeralSigners` and should be returned along with any other accounts that the user has authorized for the session. It is recommended that ephemeral signer accounts include a relevant label such as `Ephemeral Signer {n}`, tho this is not required. 

All other result parameters remain unchanged from the [base specification `authorize` method](spec.md#authorize). 

###### Errors
{: .no_toc }

- `ERROR_EPHEMERAL_COUNT_INVALID` if `ephemeral_signers` is less than zero or greater than the `max_ephemeral_signers` reported by [`get_capabilities`](#get_capabilities).

All other errors remain unchanged from the [base specification `authorize` method](spec.md#authorize).

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
    "max_transactions_per_request": <max_transactions_per_request>,
    "max_messages_per_request": <max_messages_per_request>,
    "supported_transaction_versions": [<supported_transaction_versions>, ...]
    "features": ["<feature_id>"]
    "max_ephemeral_signers": <max_ephemeral_signers>
}
```

where:

- `max_ephemeral_signers`: the maximum number of ephemeral signers that a dapp endpoint can request for a single session. 

All other result parameters remain unchanged from the [base specification `get_capabilities` method](spec.md#get_capabilities). 

###### Errors
{: .no_toc }

All errors remain unchanged from the [base specification `get_capabilities` method](spec.md#get_capabilities).