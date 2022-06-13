/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.common;

public class ProtocolContract {
    public static final String METHOD_AUTHORIZE = "authorize";
    // METHOD_AUTHORIZE takes an optional PARAMETER_IDENTITY
    public static final String PARAMETER_PRIVILEGED_METHODS = "privileged_methods";
    // METHOD_AUTHORIZE returns a RESULT_AUTH_TOKEN
    // METHOD_AUTHORIZE returns a RESULT_PUBLIC_KEY
    // METHOD_AUTHORIZE returns an optional RESULT_WALLET_URI_BASE

    public static final String METHOD_DEAUTHORIZE = "deauthorize";
    // METHOD_DEAUTHORIZE takes a PARAMETER_AUTH_TOKEN

    public static final String METHOD_REAUTHORIZE = "reauthorize";
    // METHOD_REAUTHORIZE takes an optional PARAMETER_IDENTITY
    // METHOD_REAUTHORIZE takes a PARAMETER_AUTH_TOKEN
    // METHOD_REAUTHORIZE returns a RESULT_AUTH_TOKEN

    public static final String METHOD_CLONE_AUTHORIZATION = "clone_authorization";
    // METHOD_CLONE_AUTHORIZATION takes an optional PARAMETER_IDENTITY
    // METHOD_CLONE_AUTHORIZATION takes a PARAMETER_AUTH_TOKEN
    // METHOD_CLONE_AUTHORIZATION returns a RESULT_AUTH_TOKEN

    public static final String METHOD_SIGN_TRANSACTION = "sign_transaction";
    // METHOD_SIGN_TRANSACTION takes a PARAMETER_AUTH_TOKEN
    // METHOD_SIGN_TRANSACTION takes a PARAMETER_PAYLOADS
    // METHOD_SIGN_TRANSACTION takes a PARAMETER_RETURN_SIGNED_PAYLOADS
    // METHOD_SIGN_TRANSACTION returns a RESULT_SIGNATURES
    // METHOD_SIGN_TRANSACTION returns an optional RESULT_SIGNED_PAYLOADS

    public static final String METHOD_SIGN_AND_SEND_TRANSACTION = "sign_and_send_transaction";
    // METHOD_SIGN_AND_SEND_TRANSACTION takes a PARAMETER_AUTH_TOKEN
    // METHOD_SIGN_AND_SEND_TRANSACTION takes a PARAMETER_PAYLOADS
    public static final String PARAMETER_COMMITMENT = "commitment"; // type: String (one of the COMMITMENT_* values)
    // METHOD_SIGN_AND_SEND_TRANSACTION returns a RESULT_SIGNATURES

    public static final String METHOD_SIGN_MESSAGE = "sign_message";
    // METHOD_SIGN_MESSAGE takes a PARAMETER_AUTH_TOKEN
    // METHOD_SIGN_MESSAGE takes a PARAMETER_PAYLOADS
    // METHOD_SIGN_MESSAGE takes a PARAMETER_RETURN_SIGNED_PAYLOADS
    // METHOD_SIGN_MESSAGE returns a RESULT_SIGNATURES
    // METHOD_SIGN_MESSAGE returns an optional RESULT_SIGNED_PAYLOADS

    public static final String PARAMETER_IDENTITY = "identity"; // type: JSON object
    public static final String PARAMETER_IDENTITY_URI = "uri"; // type: String (absolute URI)
    public static final String PARAMETER_IDENTITY_ICON = "icon"; // type: String (relative URI)
    public static final String PARAMETER_IDENTITY_NAME = "name"; // type: String

    public static final String PARAMETER_AUTH_TOKEN = "auth_token"; // type: String

    public static final String PARAMETER_PAYLOADS = "payloads"; // type: JSON array of base64url-encoded transactions
    public static final String PARAMETER_RETURN_SIGNED_PAYLOADS = "return_signed_payloads"; // type: Boolean

    public static final String RESULT_AUTH_TOKEN = "auth_token"; // type: String
    public static final String RESULT_PUBLIC_KEY = "pub_key"; // type: String (base58-encoded public key)
    public static final String RESULT_WALLET_URI_BASE = "wallet_uri_base"; // type: String (absolute URI)

    public static final String RESULT_SIGNATURES = "signatures"; // type: JSON array of String (base58-encoded payload signatures)
    public static final String RESULT_SIGNED_PAYLOADS = "signed_payloads"; // type: JSON array of String (base64url-encoded signed payloads)

    public static final int ERROR_REAUTHORIZE = -1;
    public static final int ERROR_AUTHORIZATION_FAILED = -2;
    public static final int ERROR_INVALID_PAYLOAD = -3;
    public static final int ERROR_NOT_SIGNED = -4;
    public static final int ERROR_NOT_COMMITTED = -5;
    public static final int ERROR_ATTEST_ORIGIN_ANDROID = -100;

    public static final String DATA_INVALID_PAYLOAD_VALID = "valid"; // Type: JSON array of Boolean

    public static final String DATA_NOT_COMMITTED_SIGNATURES = RESULT_SIGNATURES; // type: same as RESULT_SIGNATURES
    public static final String DATA_NOT_COMMITTED_COMMITMENT = "commitment"; // type: String (one of the COMMITMENT_* values) or null

    public static final String COMMITMENT_PROCESSED = "processed";
    public static final String COMMITMENT_CONFIRMED = "confirmed";
    public static final String COMMITMENT_FINALIZED = "finalized";

    public static final int TRANSACTION_MAX_SIZE_BYTES = 1232;

    private ProtocolContract() {}
}
