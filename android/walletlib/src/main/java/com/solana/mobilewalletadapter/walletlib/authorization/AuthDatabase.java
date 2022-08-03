/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;

/*package*/ class AuthDatabase extends SQLiteOpenHelper {
    private static final String TAG = AuthDatabase.class.getSimpleName();
    private static final String DATABASE_NAME_SUFFIX = "-solana-wallet-lib-auth.db";
    private static final int DATABASE_SCHEMA_VERSION = 5;

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
    /*package*/ static final String COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID = "wallet_uri_base_id"; // type: long
    /*package*/ static final String COLUMN_AUTHORIZATIONS_SCOPE = "scope"; // type: byte[]
    /*package*/ static final String COLUMN_AUTHORIZATIONS_CLUSTER = "cluster"; // type: String

    /*package*/ static final String TABLE_PUBLIC_KEYS = "public_keys";
    /*package*/ static final String COLUMN_PUBLIC_KEYS_ID = "id"; // type: long
    /*package*/ static final String COLUMN_PUBLIC_KEYS_RAW = "public_key_raw"; // type: byte[]
    /*package*/ static final String COLUMN_PUBLIC_KEYS_LABEL = "label"; // type: String

    /*package*/ static final String TABLE_WALLET_URI_BASE = "wallet_uri_base";
    /*package*/ static final String COLUMN_WALLET_URI_BASE_ID = "id"; // type: long
    /*package*/ static final String COLUMN_WALLET_URI_BASE_URI = "uri"; // type: String

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
                    COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID + " INTEGER NOT NULL," +
                    COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID + " INTEGER NOT NULL," +
                    COLUMN_AUTHORIZATIONS_SCOPE + " BLOB NOT NULL," +
                    COLUMN_AUTHORIZATIONS_CLUSTER + " TEXT NOT NULL)";
    private static final String CREATE_TABLE_PUBLIC_KEYS =
            "CREATE TABLE " + TABLE_PUBLIC_KEYS + " (" +
                    COLUMN_PUBLIC_KEYS_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_PUBLIC_KEYS_RAW + " BLOB NOT NULL," +
                    COLUMN_PUBLIC_KEYS_LABEL + " TEXT)";
    private static final String CREATE_TABLE_WALLET_URI_BASE =
            "CREATE TABLE " + TABLE_WALLET_URI_BASE + " (" +
                    COLUMN_WALLET_URI_BASE_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    COLUMN_WALLET_URI_BASE_URI + " TEXT)";

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
        db.execSQL(CREATE_TABLE_WALLET_URI_BASE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Old database schema detected; pre-v1.0.0, no DB schema backward compatibility is implemented");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_IDENTITIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_AUTHORIZATIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PUBLIC_KEYS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WALLET_URI_BASE);
        onCreate(db);
    }

    @NonNull
    public static String getDatabaseName(@NonNull AuthIssuerConfig authIssuerConfig) {
        return authIssuerConfig.name + DATABASE_NAME_SUFFIX;
    }
}
