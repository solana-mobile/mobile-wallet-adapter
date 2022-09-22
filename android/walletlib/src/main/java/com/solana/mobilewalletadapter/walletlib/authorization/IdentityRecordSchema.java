/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

/*package*/ interface IdentityRecordSchema {
    String TABLE_IDENTITIES = "identities";
    String COLUMN_IDENTITIES_ID = "id"; // type: int
    String COLUMN_IDENTITIES_NAME = "name"; // type: String
    String COLUMN_IDENTITIES_URI = "uri"; // type: String
    String COLUMN_IDENTITIES_ICON_RELATIVE_URI = "icon_relative_uri"; // type: String
    String COLUMN_IDENTITIES_SECRET_KEY = "secret_key"; // type: byte[]
    String COLUMN_IDENTITIES_SECRET_KEY_IV = "secret_key_iv"; // type: byte[]

    String CREATE_TABLE_IDENTITIES =
            "CREATE TABLE " + TABLE_IDENTITIES + " (" +
                    COLUMN_IDENTITIES_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_IDENTITIES_NAME + " TEXT NOT NULL," +
                    COLUMN_IDENTITIES_URI + " TEXT NOT NULL," +
                    COLUMN_IDENTITIES_ICON_RELATIVE_URI + " TEXT NOT NULL," +
                    COLUMN_IDENTITIES_SECRET_KEY + " BLOB NOT NULL," +
                    COLUMN_IDENTITIES_SECRET_KEY_IV + " BLOB NOT NULL)";

    String [] IDENTITY_RECORD_COLUMNS = new String[] {
            COLUMN_IDENTITIES_ID,
            COLUMN_IDENTITIES_NAME,
            COLUMN_IDENTITIES_URI,
            COLUMN_IDENTITIES_ICON_RELATIVE_URI,
            COLUMN_IDENTITIES_SECRET_KEY,
            COLUMN_IDENTITIES_SECRET_KEY_IV
    };
}
