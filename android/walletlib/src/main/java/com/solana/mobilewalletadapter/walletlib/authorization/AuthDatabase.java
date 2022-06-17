/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

/*package*/ class AuthDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME_SUFFIX = "-solana-wallet-lib-auth.db";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    /*package*/ static final String TABLE_IDENTITIES = "identities";
    /*package*/ static final String COLUMN_IDENTITIES_ID = "id"; // type: int
    /*package*/ static final String COLUMN_IDENTITIES_NAME = "name"; // type: String
    /*package*/ static final String COLUMN_IDENTITIES_URI = "uri"; // type: String
    /*package*/ static final String COLUMN_IDENTITIES_ICON_RELATIVE_URI = "icon_relative_uri"; // type: String
    /*package*/ static final String COLUMN_IDENTITIES_SECRET_KEY = "secret_key"; // type: byte[]
    /*package*/ static final String COLUMN_IDENTITIES_SECRET_KEY_IV = "secret_key_iv"; // type: byte[]

    /*package*/ static final String TABLE_AUTHORIZATIONS = "authorizations";
    /*package*/ static final String COLUMN_AUTHORIZATIONS_ID = "id"; // type: int
    /*package*/ static final String COLUMN_AUTHORIZATIONS_IDENTITY_ID = "identity_id"; // type: long
    /*package*/ static final String COLUMN_AUTHORIZATIONS_ISSUED = "issued"; // type: long
    /*package*/ static final String COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID = "public_key_id"; // type: long

    /*package*/ static final String TABLE_PUBLIC_KEYS = "public_keys";
    /*package*/ static final String COLUMN_PUBLIC_KEYS_ID = "id";
    /*package*/ static final String COLUMN_PUBLIC_KEYS_BASE58 = "public_key_base58";

    private static final String CREATE_TABLE_IDENTITIES =
            "CREATE TABLE " + TABLE_IDENTITIES + " (" +
                    COLUMN_IDENTITIES_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_IDENTITIES_NAME + " TEXT NOT NULL," +
                    COLUMN_IDENTITIES_URI + " TEXT NOT NULL," +
                    COLUMN_IDENTITIES_ICON_RELATIVE_URI + " TEXT NOT NULL," +
                    COLUMN_IDENTITIES_SECRET_KEY + " BLOB NOT NULL," +
                    COLUMN_IDENTITIES_SECRET_KEY_IV + " BLOB NOT NULL)";
    private static final String CREATE_TABLE_AUTHORIZATIONS =
            "CREATE TABLE " + TABLE_AUTHORIZATIONS + " (" +
                    COLUMN_AUTHORIZATIONS_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_AUTHORIZATIONS_IDENTITY_ID + " INTEGER NOT NULL," +
                    COLUMN_AUTHORIZATIONS_ISSUED + " INTEGER NOT NULL," +
                    COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID + " INTEGER NOT NULL)";
    private static final String CREATE_TABLE_PUBLIC_KEYS =
            "CREATE TABLE " + TABLE_PUBLIC_KEYS + " (" +
                    COLUMN_PUBLIC_KEYS_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_PUBLIC_KEYS_BASE58 + " TEXT NOT NULL)";

    AuthDatabase(@NonNull Context context, @NonNull AuthIssuerConfig authIssuerConfig) {
        super(context, getDatabaseName(authIssuerConfig), null, DATABASE_SCHEMA_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_IDENTITIES);
        db.execSQL(CREATE_TABLE_AUTHORIZATIONS);
        db.execSQL(CREATE_TABLE_PUBLIC_KEYS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new RuntimeException("Only a single DB schema is defined; no upgrade support is needed yet");
    }

    @NonNull
    public static String getDatabaseName(@NonNull AuthIssuerConfig authIssuerConfig) {
        return authIssuerConfig.name + DATABASE_NAME_SUFFIX;
    }
}
