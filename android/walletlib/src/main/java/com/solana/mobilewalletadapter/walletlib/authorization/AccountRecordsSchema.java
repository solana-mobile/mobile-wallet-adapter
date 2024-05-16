package com.solana.mobilewalletadapter.walletlib.authorization;

/*package*/ interface AccountRecordsSchema {
    String TABLE_ACCOUNTS = "accounts";
    String COLUMN_ACCOUNTS_ID = "id"; // type: long
    String COLUMN_ACCOUNTS_PARENT_ID = "parent_id"; // type: long
    String COLUMN_ACCOUNTS_PUBLIC_KEY_RAW = "public_key_raw"; // type: byte[]
    String COLUMN_ACCOUNTS_LABEL = "label"; // type: String
    String COLUMN_ACCOUNTS_ICON = "icon"; // type: String
    String COLUMN_ACCOUNTS_CHAINS = "chains"; // type: String
    String COLUMN_ACCOUNTS_FEATURES = "features"; // type: String

    String CREATE_TABLE_ACCOUNTS =
            "CREATE TABLE " + TABLE_ACCOUNTS + " (" +
                    COLUMN_ACCOUNTS_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_ACCOUNTS_PARENT_ID + " INTEGER NOT NULL," +
                    COLUMN_ACCOUNTS_PUBLIC_KEY_RAW + " BLOB NOT NULL," +
                    COLUMN_ACCOUNTS_LABEL + " TEXT," +
                    COLUMN_ACCOUNTS_ICON + " TEXT," +
                    COLUMN_ACCOUNTS_CHAINS + " TEXT," +
                    COLUMN_ACCOUNTS_FEATURES + " TEXT)";

    String[] ACCOUNTS_COLUMNS = new String[]{
            COLUMN_ACCOUNTS_ID,
            COLUMN_ACCOUNTS_PARENT_ID,
            COLUMN_ACCOUNTS_PUBLIC_KEY_RAW,
            COLUMN_ACCOUNTS_LABEL,
            COLUMN_ACCOUNTS_ICON,
            COLUMN_ACCOUNTS_CHAINS,
            COLUMN_ACCOUNTS_FEATURES
    };
}
