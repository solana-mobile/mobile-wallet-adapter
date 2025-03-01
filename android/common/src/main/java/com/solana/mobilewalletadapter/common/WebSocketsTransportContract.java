/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common;

import androidx.annotation.IntRange;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class WebSocketsTransportContract {
    public static final String WEBSOCKETS_LOCAL_SCHEME = "ws";
    public static final String WEBSOCKETS_LOCAL_HOST = "127.0.0.1";
    public static final String WEBSOCKETS_LOCAL_PATH = "/solana-wallet";
    public static final int WEBSOCKETS_LOCAL_PORT_MIN = 49152;
    public static final int WEBSOCKETS_LOCAL_PORT_MAX = 65535;
    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from=WEBSOCKETS_LOCAL_PORT_MIN, to=WEBSOCKETS_LOCAL_PORT_MAX)
    public @interface LocalPortRange {}

    public static final String WEBSOCKETS_REFLECTOR_SCHEME = "wss";
    public static final String WEBSOCKETS_REFLECTOR_PATH = "/reflect";
    public static final String WEBSOCKETS_REFLECTOR_ID_QUERY = "id";
    public static final long WEBSOCKETS_REFLECTOR_ID_MIN = 0;
    public static final long WEBSOCKETS_REFLECTOR_ID_MAX = 9007199254740991L; // 2^53 - 1
    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from=WEBSOCKETS_REFLECTOR_ID_MIN, to=WEBSOCKETS_REFLECTOR_ID_MAX)
    public @interface ReflectorIdRange {}

    public static final String WEBSOCKETS_PROTOCOL = "com.solana.mobilewalletadapter.v1";
    public static final String WEBSOCKETS_BASE64_PROTOCOL = "com.solana.mobilewalletadapter.v1.base64";

    private WebSocketsTransportContract() {}
}
