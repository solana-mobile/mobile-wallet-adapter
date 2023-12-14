/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;

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
            publicKeysDao.deleteUnreferencedPublicKeys();

            db.execSQL(AccountRecordsSchema.CREATE_TABLE_ACCOUNTS);
            db.execSQL("ALTER TABLE " + AuthorizationsSchema.TABLE_AUTHORIZATIONS +
                    " RENAME COLUMN " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                    " TO " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ACCOUNT_ID);
            db.execSQL("ALTER TABLE " + AuthorizationsSchema.TABLE_AUTHORIZATIONS +
                    " RENAME COLUMN " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_CLUSTER +
                    " TO " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_CHAIN);

            final List<PublicKey> publicKeys = publicKeysDao.getPublicKeys();
            if (!publicKeys.isEmpty()) {
                AccountRecordsDao accountRecordsDao = new AccountRecordsDao(db);
                for (PublicKey publicKey : publicKeys) {
                    final long accountId = accountRecordsDao.insert(publicKey.publicKeyRaw,
                            publicKey.accountLabel, null, null, null);

                    // the public keys will be sorted by their id, and the new account ID will
                    // always be >= the existing public key ID so it is safe to update these values
                    // in place. For publicKey.id p(n) and accountId a(n), p(n) > p(n-1) and
                    // a(n) >= p(n), therefore a(n) > p(n-1). So the 'WHERE account_id = p(n)'
                    // condition here will not collide with previously updated entries.
                    db.execSQL("UPDATE " + AuthorizationsSchema.TABLE_AUTHORIZATIONS +
                            " SET " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ACCOUNT_ID + " = " + accountId +
                            " WHERE " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ACCOUNT_ID + " = " + publicKey.id);
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
