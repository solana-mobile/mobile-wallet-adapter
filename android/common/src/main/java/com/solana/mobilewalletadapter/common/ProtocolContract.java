/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.common;

public class ProtocolContract {
    public static final String HELLO_MESSAGE_TYPE = "m"; // type: String

    public static final String HELLO_REQ_MESSAGE = "HELLO_REQ";
    public static final String HELLO_REQ_PUBLIC_KEY = "qd"; // type: JWS

    public static final String HELLO_RSP_MESSAGE = "HELLO_RSP";
    public static final String HELLO_RSP_PUBLIC_KEY = "qw"; // type: JWK

    public static final int ERROR_REAUTHORIZE = -1;
    public static final int ERROR_AUTHORIZATION_FAILED = -2;
    public static final int ERROR_INVALID_TRANSACTION = -3;
    public static final int ERROR_NOT_SIGNED = -4;
    public static final int ERROR_ATTEST_ORIGIN_ANDROID = -100;

    private ProtocolContract() {}
}
