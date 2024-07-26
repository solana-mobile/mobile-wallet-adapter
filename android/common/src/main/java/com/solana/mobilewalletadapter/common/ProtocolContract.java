/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common;

public class ProtocolContract {
    public static final String METHOD_AUTHORIZE = "authorize";
    // METHOD_AUTHORIZE takes an optional PARAMETER_IDENTITY
    // METHOD_AUTHORIZE takes an optional PARAMETER_AUTH_TOKEN
    // METHOD_AUTHORIZE takes an optional PARAMETER_CHAIN
    // METHOD_AUTHORIZE takes an optional PARAMETER_FEATURES
    // METHOD_AUTHORIZE takes an optional PARAMETER_ADDRESSES
    // METHOD_AUTHORIZE returns a RESULT_AUTH_TOKEN
    // METHOD_AUTHORIZE returns a RESULT_ACCOUNTS
    // METHOD_AUTHORIZE returns an optional RESULT_WALLET_URI_BASE

    public static final String METHOD_DEAUTHORIZE = "deauthorize";
    // METHOD_DEAUTHORIZE takes a PARAMETER_AUTH_TOKEN

    @Deprecated
    public static final String METHOD_REAUTHORIZE = "reauthorize";
    // METHOD_REAUTHORIZE takes an optional PARAMETER_IDENTITY
    // METHOD_REAUTHORIZE takes a PARAMETER_AUTH_TOKEN
    // METHOD_REAUTHORIZE returns a RESULT_AUTH_TOKEN
    // METHOD_REAUTHORIZE returns a RESULT_ACCOUNTS
    // METHOD_REAUTHORIZE returns an optional RESULT_WALLET_URI_BASE

    public static final String METHOD_CLONE_AUTHORIZATION = "clone_authorization";
    // METHOD_CLONE_AUTHORIZATION returns a RESULT_AUTH_TOKEN

    public static final String METHOD_GET_CAPABILITIES = "get_capabilities";
    public static final String RESULT_MAX_TRANSACTIONS_PER_REQUEST = "max_transactions_per_request"; // type: Number
    public static final String RESULT_MAX_MESSAGES_PER_REQUEST = "max_messages_per_request"; // type: Number
    public static final String RESULT_SUPPORTED_TRANSACTION_VERSIONS = "supported_transaction_versions"; // type: JSON array of any primitive datatype
    public static final String RESULT_SUPPORTED_FEATURES = "features"; // type: JSON array of String (feature identifiers)
    @Deprecated
    public static final String RESULT_SUPPORTS_CLONE_AUTHORIZATION = "supports_clone_authorization"; // type: Boolean
    @Deprecated
    public static final String RESULT_SUPPORTS_SIGN_AND_SEND_TRANSACTIONS = "supports_sign_and_send_transactions"; // type: Boolean

    @Deprecated(since = "2.0.0", forRemoval = true)
    public static final String METHOD_SIGN_TRANSACTIONS = "sign_transactions";
    // METHOD_SIGN_TRANSACTIONS takes a PARAMETER_PAYLOADS
    // METHOD_SIGN_TRANSACTIONS returns a RESULT_SIGNED_PAYLOADS

    public static final String METHOD_SIGN_AND_SEND_TRANSACTIONS = "sign_and_send_transactions";
    // METHOD_SIGN_AND_SEND_TRANSACTIONS takes a PARAMETER_PAYLOADS
    public static final String PARAMETER_OPTIONS = "options"; // type: JSON object
    public static final String PARAMETER_OPTIONS_MIN_CONTEXT_SLOT = "min_context_slot"; // type: Number
    public static final String PARAMETER_OPTIONS_COMMITMENT = "commitment"; // type: String
    public static final String PARAMETER_OPTIONS_SKIP_PREFLIGHT = "skip_preflight"; // type: String
    public static final String PARAMETER_OPTIONS_MAX_RETRIES = "max_retries"; // type: Number
    public static final String PARAMETER_OPTIONS_WAIT_FOR_COMMITMENT = "wait_for_commitment_to_send_next_transaction"; // type: Boolean
    public static final String RESULT_SIGNATURES = "signatures"; // type: JSON array of String (base64-encoded payload signatures)

    public static final String METHOD_SIGN_MESSAGES = "sign_messages";
    public static final String PARAMETER_ADDRESSES = "addresses"; // type: JSON array of String (base64-encoded addresses)
    // METHOD_SIGN_MESSAGES takes a PARAMETER_PAYLOADS
    // METHOD_SIGN_MESSAGES returns a RESULT_SIGNED_PAYLOADS

    public static final String PARAMETER_IDENTITY = "identity"; // type: JSON object
    public static final String PARAMETER_IDENTITY_URI = "uri"; // type: String (absolute URI)
    public static final String PARAMETER_IDENTITY_ICON = "icon"; // type: String (relative URI)
    public static final String PARAMETER_IDENTITY_NAME = "name"; // type: String

    @Deprecated // alias for PARAMETER_CHAIN
    public static final String PARAMETER_CLUSTER = "cluster"; // type: String (one of the CLUSTER_* values)

    public static final String PARAMETER_CHAIN = "chain"; // type: String (one of the CHAIN_* values)

    public static final String PARAMETER_FEATURES = "features"; // type: JSON array of String (feature identifiers)

    public static final String PARAMETER_AUTH_TOKEN = "auth_token"; // type: String

    public static final String PARAMETER_SIGN_IN_PAYLOAD = "sign_in_payload"; // type: String

    public static final String PARAMETER_PAYLOADS = "payloads"; // type: JSON array of String (base64-encoded payloads)

    public static final String RESULT_AUTH_TOKEN = "auth_token"; // type: String
    public static final String RESULT_ACCOUNTS = "accounts"; // type: JSON array of Account
    public static final String RESULT_ACCOUNTS_ADDRESS = "address"; // type: String (base64-encoded addresses)
    public static final String RESULT_ACCOUNTS_DISPLAY_ADDRESS = "display_address"; // type: String
    public static final String RESULT_ACCOUNTS_DISPLAY_ADDRESS_FORMAT = "display_address_format"; // type: String
    public static final String RESULT_ACCOUNTS_LABEL = "label"; // type: String
    public static final String RESULT_ACCOUNTS_ICON = "icon"; // type: String
    public static final String RESULT_ACCOUNTS_CHAINS = "chains"; // type: String
    // RESULT_ACCOUNTS optionally includes a RESULT_SUPPORTED_FEATURES

    public static final String RESULT_WALLET_URI_BASE = "wallet_uri_base"; // type: String (absolute URI)
    public static final String RESULT_WALLET_ICON = "wallet_icon"; // type: String (data URI)

    public static final String RESULT_SIGN_IN = "sign_in_result"; // type JSON object
    public static final String RESULT_SIGN_IN_ADDRESS = "address"; // type: String (address)
    public static final String RESULT_SIGN_IN_SIGNED_MESSAGE = "signed_message"; // type: String (base64-encoded signed message)
    public static final String RESULT_SIGN_IN_SIGNATURE = "signature"; // type: String (base64-encoded signature)
    public static final String RESULT_SIGN_IN_SIGNATURE_TYPE = "signature_type"; // type: String

    public static final String RESULT_SIGNED_PAYLOADS = "signed_payloads"; // type: JSON array of String (base64-encoded signed payloads)

    // Keep these in sync with `mobile-wallet-adapter-protocol/src/errors.ts`.
    public static final int ERROR_AUTHORIZATION_FAILED = -1;
    public static final int ERROR_INVALID_PAYLOADS = -2;
    public static final int ERROR_NOT_SIGNED = -3;
    public static final int ERROR_NOT_SUBMITTED = -4;
    public static final int ERROR_NOT_CLONED = -5;
    public static final int ERROR_TOO_MANY_PAYLOADS = -6;
    public static final int ERROR_CLUSTER_NOT_SUPPORTED = -7;
    public static final int ERROR_ATTEST_ORIGIN_ANDROID = -100;

    public static final String DATA_INVALID_PAYLOADS_VALID = "valid"; // Type: JSON array of Boolean

    public static final String DATA_NOT_SUBMITTED_SIGNATURES = RESULT_SIGNATURES; // type: same as RESULT_SIGNATURES

    public static final String CLUSTER_MAINNET_BETA = "mainnet-beta";
    public static final String CLUSTER_TESTNET = "testnet";
    public static final String CLUSTER_DEVNET = "devnet";

    public static final String CHAIN_SOLANA_MAINNET = "solana:mainnet";
    public static final String CHAIN_SOLANA_TESTNET = "solana:testnet";
    public static final String CHAIN_SOLANA_DEVNET = "solana:devnet";

    public static final String NAMESPACE_SOLANA = "solana";

    // Mandatory Features
    public static final String FEATURE_ID_SIGN_MESSAGES = "solana:signMessages";
    public static final String FEATURE_ID_SIGN_AND_SEND_TRANSACTIONS = "solana:signAndSendTransaction";

    // Optional Features
    public static final String FEATURE_ID_SIGN_IN_WITH_SOLANA = "solana:signInWithSolana";
    public static final String FEATURE_ID_CLONE_AUTHORIZATION = "solana:cloneAuthorization";
    public static final String FEATURE_ID_SIGN_TRANSACTIONS = "solana:signTransactions";

    private ProtocolContract() {}
}
