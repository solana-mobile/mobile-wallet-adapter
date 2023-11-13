/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.util.JsonPack;

import java.util.Arrays;
import java.util.List;

/*package*/ class AuthDatabase extends SQLiteOpenHelper {
    private static final String TAG = AuthDatabase.class.getSimpleName();
    private static final String DATABASE_NAME_SUFFIX = "-solana-wallet-lib-auth.db";
    private static final int DATABASE_SCHEMA_VERSION = 6;

    AuthDatabase(@NonNull Context context, @NonNull AuthIssuerConfig authIssuerConfig) {
        super(context, getDatabaseName(authIssuerConfig), null, DATABASE_SCHEMA_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(IdentityRecordSchema.CREATE_TABLE_IDENTITIES);
        db.execSQL(AuthorizationsSchema.CREATE_TABLE_AUTHORIZATIONS);
        db.execSQL(AccountRecordsSchema.CREATE_TABLE_ACCOUNTS);
        db.execSQL(WalletUriBaseSchema.CREATE_TABLE_WALLET_URI_BASE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            Log.w(TAG, "Old database schema detected; pre-v1.0.0, no DB schema backward compatibility is implemented");
            db.execSQL("DROP TABLE IF EXISTS " + IdentityRecordSchema.TABLE_IDENTITIES);
            db.execSQL("DROP TABLE IF EXISTS " + AuthorizationsSchema.TABLE_AUTHORIZATIONS);
            db.execSQL("DROP TABLE IF EXISTS " + PublicKeysSchema.TABLE_PUBLIC_KEYS);
            db.execSQL("DROP TABLE IF EXISTS " + WalletUriBaseSchema.TABLE_WALLET_URI_BASE);
            onCreate(db);
        } else {
            Log.w(TAG, "Old database schema detected; pre-v2.0.0, migrating public keys to account records");
            final PublicKeysDao publicKeysDao = new PublicKeysDao(db);
            final List<PublicKey> publicKeys = publicKeysDao.getAuthorizedPublicKeys();

            db.execSQL(AccountRecordsSchema.CREATE_TABLE_ACCOUNTS);
            if (!publicKeys.isEmpty()) {
                AccountRecordsDao accountRecordsDao = new AccountRecordsDao(db);
                for (PublicKey publicKey : publicKeys) {
                    Log.d(TAG, "migrating Public Key: " + publicKey.accountLabel + " (" + Base64.encodeToString(publicKey.publicKeyRaw, Base64.NO_WRAP) + ")");
                    accountRecordsDao.insert(publicKey.publicKeyRaw, publicKey.accountLabel,
                            null, null, null);
                }
            }

            db.execSQL("DROP TABLE IF EXISTS " + PublicKeysSchema.TABLE_PUBLIC_KEYS);
        }
    }

    @NonNull
    public static String getDatabaseName(@NonNull AuthIssuerConfig authIssuerConfig) {
        return authIssuerConfig.name + DATABASE_NAME_SUFFIX;
    }
}
