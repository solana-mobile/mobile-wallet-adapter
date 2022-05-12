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

    private ProtocolContract() {}
}
