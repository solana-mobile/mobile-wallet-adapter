---
layout: default
title: Mobile Wallet Adapter One-Off Transactions feature extension specification
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

TODO

## Summary

"One Off" transactions are transactions that do not require a previous authorization to complete. This is effectively a combination of `authorize` + `sign_and_send_transactions` into a single request. Wallet endpoints supporting this feature should show a single UI modal to sign the rquested transactions.

## User stories

These user stories outline the goals and user experiences that this feature aims to enable.

1. As a user, I want to be able to sign transactions with fewer steps and interactions so that I can interact with dapps in a seamless and less intrusive flow.
2. As a dapp developer, I want the ability to send a one off request to wallet endpoints without needing to establish an authorized session with the wallet so that I can more easily send transactions that do not require subsequent requests. 

## Requirements

These requirements are derived from the user stories, and exist to guide feature specification design decisions.

1. One off transaction requests should .

   _Rationale: ..._

# Specification

This specification is an extension of the [Mobile Wallet Adapter Specification](spec.md). All existing terminology and functionality is inherited by this extension unless explicitly noted.

## Feature Identifier

The namespace for this feature is `solana` and the name (reference) of the feature is `oneOffTransactions`. The resulting feature identifier for this feature is `solana:oneOffTransactions`.

## Wallet RPC interface

### Extensions to Existing RPC Methods

A detailed description of any existing RPC methods that are extended by this feature, including added input parameters, result payloads, and possible new errors returned by each method. 

### New RPC Methods

A detailed description of any new RPC methods specified by this feature extension, including input parameters, result payloads, and possible errors returned by each method. 

### Non-privileged methods

Non-privileged methods do not require the current session to be in an authorized state to invoke them (though they may still accept an `auth_token` to provide their functionality).

#### one_off_sign_and_send_transactions

##### JSON-RPC method specification

###### Method 
{: .no_toc }

```
one_off_sign_and_send_transactions
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
    “payloads”: [“<transaction>”, ...],
    "options": {
        “min_context_slot”: <min_context_slot>,
    }
}
```

where:

- `identity`: a JSON object, containing:
  - `uri`: (optional) a URI representing the web address associated with the dapp endpoint making this authorization request. If present, it must be an absolute, hierarchical URI.
  - `icon`: (optional) a relative path (from `uri`) to an image asset file of an icon identifying the dapp endpoint making this authorization request
  - `name`: (optional) the display name for this dapp endpoint
- `cluster`: (optional) if set, the Solana network cluster with which the dapp endpoint intends to interact; supported values include `mainnet-beta`, `testnet`, `devnet`. If not set, defaults to `mainnet-beta`.
- `payloads`: one or more base64-encoded transaction payload to sign
- `options`: (optional) a JSON object, containing:
  - `min_context_slot`: (optional) if set, the minimum slot number at which to perform preflight transaction checks

###### Result
{: .no_toc }

```
{
    “signatures”: [“<transaction_signature>”, ...],
    “wallet_uri_base”: “<wallet_uri_base>”,
}
```

where:

- `signatures`: the corresponding base64-encoded transaction signatures
- `wallet_uri_base`: (optional) if this wallet endpoint has an [endpoint-specific URI](#endpoint-specific-uris) that the dapp endpoint should use for subsequent connections, this member will be included in the result object. The dapp endpoint should use this URI for all subsequent connections where it expects to use this `auth_token`.

###### Errors
{: .no_toc }

- `-32601` (Method not found) if `one_off_sign_and_send_transactions` is not supported by this wallet endpoint
- `-32602` (Invalid params) if the params object does not match the format defined above
- `ERROR_AUTHORIZATION_FAILED` if the wallet endpoint did not authorize access to the requested privileged methods
- `ERROR_CLUSTER_NOT_SUPPORTED` if the wallet endpoint does not support the requested Solana cluster
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

This method allows the dapp endpoint to request transactions to be signed and sent by a wallet endpoint without requiring the establishment of an authorized session.

Wallet endpoints should make every effort possible to [verify the authenticity](#dapp-identity-verification) of the presented identity. While the `uri` parameter is optional, it is strongly recommended - without it, the wallet endpoint may not be able to verify the authenticity of the dapp.

The `cluster` parameter allows the dapp endpoint to select a specific Solana cluster with which to interact. This is relevant for both [`sign_transactions`](#sign_transactions), where a wallet may refuse to sign transactions without a currently valid blockhash, and for [`sign_and_send_transactions`](#sign_and_send_transactions), where the wallet endpoint must know which cluster to submit the transactions to. This parameter would normally be used to select a cluster other than `mainnet-beta` for dapp development and testing purposes. Under normal circumstances, this field should be omitted, in which case the wallet endpoint will interact with the `mainnet-beta` cluster.

The wallet endpoint should attempt to simulate the transactions provided by `payloads` and present them to the user for approval (if applicable). If approved (or if it does not require approval), the wallet endpoint should verify the transactions, sign them with the private keys for the authorized addresses, submit them to the network, and return the transaction signatures to the dapp endpoint.

`options` allows customization of how the wallet endpoint processes the transactions it sends to the Solana network. If specified, `min_context_slot` specifies the minimum slot number that the transactions should be evaluated at. This allows the wallet endpoint to wait for its network RPC node to reach the same point in time as the node used by the dapp endpoint, ensuring that, e.g., the recent blockhash encoded in the transactions will be available.

###### Non-normative commentary

This method is optional, to support immediate completion of transactions with fewer steps for a better user experience for dapps that do not require multiple requests to a wallet.  

it does not allow the dapp endpoint to specify the network RPC server to submit the transaction to; that is at the discretion of the wallet endpoint. If this is a detail that matters to the dapp endpoint, it should instead use the `sign_transactions` method and submit the transaction to a network RPC server of its choosing.

It is recommended that dapp endpoints verify that each transaction reached an appropriate level of commitment (typically either `confirmed` or `finalized`).
