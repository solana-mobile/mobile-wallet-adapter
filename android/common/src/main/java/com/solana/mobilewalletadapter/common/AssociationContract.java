/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common;

public class AssociationContract {
    public static final String SCHEME_MOBILE_WALLET_ADAPTER = "solana-wallet";

    public static final String PARAMETER_ASSOCIATION_TOKEN = "association";

    public static final String LOCAL_PATH_SUFFIX = "v1/associate/local";
    public static final String LOCAL_PARAMETER_PORT = "port"; // type: Int

    public static final String REMOTE_PATH_SUFFIX = "v1/associate/remote";
    public static final String REMOTE_PARAMETER_REFLECTOR_HOST_AUTHORITY = "reflector"; // type: String
    public static final String REMOTE_PARAMETER_REFLECTOR_ID = "id"; // type: Long

    private AssociationContract() {}
}