/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

@Deprecated
/*package*/ interface PublicKeysSchema {

    String TABLE_PUBLIC_KEYS = "public_keys";
    String COLUMN_PUBLIC_KEYS_ID = "id"; // type: long
    String COLUMN_PUBLIC_KEYS_RAW = "public_key_raw"; // type: byte[]
    String COLUMN_PUBLIC_KEYS_LABEL = "label"; // type: String

    String CREATE_TABLE_PUBLIC_KEYS =
            "CREATE TABLE " + TABLE_PUBLIC_KEYS + " (" +
                    COLUMN_PUBLIC_KEYS_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_PUBLIC_KEYS_RAW + " BLOB NOT NULL," +
                    COLUMN_PUBLIC_KEYS_LABEL + " TEXT)";

    String[] PUBLIC_KEYS_COLUMNS = new String[]{
            COLUMN_PUBLIC_KEYS_ID,
            COLUMN_PUBLIC_KEYS_RAW,
            COLUMN_PUBLIC_KEYS_LABEL
    };
}
