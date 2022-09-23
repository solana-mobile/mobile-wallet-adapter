/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

/*package*/ interface WalletUriBaseSchema {
    String TABLE_WALLET_URI_BASE = "wallet_uri_base";
    String COLUMN_WALLET_URI_BASE_ID = "id"; // type: long
    String COLUMN_WALLET_URI_BASE_URI = "uri"; // type: String

    String CREATE_TABLE_WALLET_URI_BASE =
            "CREATE TABLE " + TABLE_WALLET_URI_BASE + " (" +
                    COLUMN_WALLET_URI_BASE_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_WALLET_URI_BASE_URI + " TEXT)";

    String[] WALLET_URI_BASE_COLUMNS = new String[]{
            COLUMN_WALLET_URI_BASE_ID,
            COLUMN_WALLET_URI_BASE_URI
    };
}
