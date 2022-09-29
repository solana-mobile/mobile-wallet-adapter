/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common;

public class ProtocolContract {
    public static final String METHOD_AUTHORIZE = "authorize";
    // METHOD_AUTHORIZE takes an optional PARAMETER_IDENTITY
    public static final String PARAMETER_CLUSTER = "cluster"; // type: String (one of the CLUSTER_* values)
    // METHOD_AUTHORIZE returns a RESULT_AUTH_TOKEN
    // METHOD_AUTHORIZE returns a RESULT_ACCOUNTS
    // METHOD_AUTHORIZE returns an optional RESULT_WALLET_URI_BASE

    public static final String METHOD_DEAUTHORIZE = "deauthorize";
    // METHOD_DEAUTHORIZE takes a PARAMETER_AUTH_TOKEN

    public static final String METHOD_REAUTHORIZE = "reauthorize";
    // METHOD_REAUTHORIZE takes an optional PARAMETER_IDENTITY
    // METHOD_REAUTHORIZE takes a PARAMETER_AUTH_TOKEN
    // METHOD_REAUTHORIZE returns a RESULT_AUTH_TOKEN
    // METHOD_REAUTHORIZE returns a RESULT_ACCOUNTS
    // METHOD_REAUTHORIZE returns an optional RESULT_WALLET_URI_BASE

    public static final String METHOD_CLONE_AUTHORIZATION = "clone_authorization";
    // METHOD_CLONE_AUTHORIZATION returns a RESULT_AUTH_TOKEN

    public static final String METHOD_GET_CAPABILITIES = "get_capabilities";
    public static final String RESULT_SUPPORTS_CLONE_AUTHORIZATION = "supports_clone_authorization"; // type: Boolean
    public static final String RESULT_SUPPORTS_SIGN_AND_SEND_TRANSACTIONS = "supports_sign_and_send_transactions"; // type: Boolean
    public static final String RESULT_MAX_TRANSACTIONS_PER_REQUEST = "max_transactions_per_request"; // type: Number
    public static final String RESULT_MAX_MESSAGES_PER_REQUEST = "max_messages_per_request"; // type: Number
    public static final String RESULT_SUPPORTED_TRANSACTION_VERSIONS = "supported_transaction_versions"; // type: JSON array of any primitive datatype

    public static final String METHOD_SIGN_TRANSACTIONS = "sign_transactions";
    // METHOD_SIGN_TRANSACTIONS takes a PARAMETER_PAYLOADS
    // METHOD_SIGN_TRANSACTIONS returns a RESULT_SIGNED_PAYLOADS

    public static final String METHOD_SIGN_AND_SEND_TRANSACTIONS = "sign_and_send_transactions";
    // METHOD_SIGN_AND_SEND_TRANSACTIONS takes a PARAMETER_PAYLOADS
    public static final String PARAMETER_OPTIONS = "options"; // type: JSON object
    public static final String PARAMETER_OPTIONS_MIN_CONTEXT_SLOT = "min_context_slot"; // type: Number
    public static final String RESULT_SIGNATURES = "signatures"; // type: JSON array of String (base64-encoded payload signatures)

    public static final String METHOD_SIGN_MESSAGES = "sign_messages";
    public static final String PARAMETER_ADDRESSES = "addresses"; // type: JSON array of String (base64-encoded addresses)
    // METHOD_SIGN_MESSAGES takes a PARAMETER_PAYLOADS
    // METHOD_SIGN_MESSAGES returns a RESULT_SIGNED_PAYLOADS

    public static final String PARAMETER_IDENTITY = "identity"; // type: JSON object
    public static final String PARAMETER_IDENTITY_URI = "uri"; // type: String (absolute URI)
    public static final String PARAMETER_IDENTITY_ICON = "icon"; // type: String (relative URI)
    public static final String PARAMETER_IDENTITY_NAME = "name"; // type: String

    public static final String PARAMETER_AUTH_TOKEN = "auth_token"; // type: String

    public static final String PARAMETER_PAYLOADS = "payloads"; // type: JSON array of String (base64-encoded payloads)

    public static final String RESULT_AUTH_TOKEN = "auth_token"; // type: String
    public static final String RESULT_ACCOUNTS = "accounts"; // type: JSON array of Account
    public static final String RESULT_ACCOUNTS_ADDRESS = "address"; // type: String (base64-encoded addresses)
    public static final String RESULT_ACCOUNTS_LABEL = "label"; // type: String

    public static final String RESULT_WALLET_URI_BASE = "wallet_uri_base"; // type: String (absolute URI)

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

    private ProtocolContract() {}
}
