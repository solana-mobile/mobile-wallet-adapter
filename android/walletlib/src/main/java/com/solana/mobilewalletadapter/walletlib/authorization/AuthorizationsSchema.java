/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

/*package*/ interface AuthorizationsSchema {
    String TABLE_AUTHORIZATIONS = "authorizations";
    String COLUMN_AUTHORIZATIONS_ID = "id"; // type: int
    String COLUMN_AUTHORIZATIONS_IDENTITY_ID = "identity_id"; // type: long
    String COLUMN_AUTHORIZATIONS_ISSUED = "issued"; // type: long
    String COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID = "wallet_uri_base_id"; // type: long
    String COLUMN_AUTHORIZATIONS_SCOPE = "scope"; // type: byte[]
    String COLUMN_AUTHORIZATIONS_CHAIN = "chain"; // type: String

    @Deprecated
    String COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID = "public_key_id"; // type: long
    @Deprecated
    String COLUMN_AUTHORIZATIONS_CLUSTER = "cluster"; // type: String
    @Deprecated
    String COLUMN_AUTHORIZATIONS_ACCOUNT_ID = "account_id"; // type: long

    String CREATE_TABLE_AUTHORIZATIONS =
            "CREATE TABLE " + TABLE_AUTHORIZATIONS + " (" +
                    COLUMN_AUTHORIZATIONS_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_AUTHORIZATIONS_IDENTITY_ID + " INTEGER NOT NULL," +
                    COLUMN_AUTHORIZATIONS_ISSUED + " INTEGER NOT NULL," +
                    COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID + " INTEGER NOT NULL," +
                    COLUMN_AUTHORIZATIONS_SCOPE + " BLOB NOT NULL," +
                    COLUMN_AUTHORIZATIONS_CHAIN + " TEXT NOT NULL)";
}
