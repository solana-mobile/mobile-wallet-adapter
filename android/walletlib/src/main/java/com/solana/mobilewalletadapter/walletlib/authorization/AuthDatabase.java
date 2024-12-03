/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;

/*package*/ class AuthDatabase extends SQLiteOpenHelper {
    private static final String TAG = AuthDatabase.class.getSimpleName();
    private static final String DATABASE_NAME_SUFFIX = "-solana-wallet-lib-auth.db";
    private static final int DATABASE_SCHEMA_VERSION = 7;

    AuthDatabase(@NonNull Context context, @NonNull AuthIssuerConfig authIssuerConfig) {
        super(context, getDatabaseName(authIssuerConfig), null, DATABASE_SCHEMA_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
    }

    @Override    public void onCreate(SQLiteDatabase db) {
        db.execSQL(IdentityRecordSchema.CREATE_TABLE_IDENTITIES);
        db.execSQL(AuthorizationsSchema.CREATE_TABLE_AUTHORIZATIONS);
        db.execSQL(AccountRecordsSchema.CREATE_TABLE_ACCOUNTS);
        db.execSQL(WalletUriBaseSchema.CREATE_TABLE_WALLET_URI_BASE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion > newVersion) {
            // The documentation for this method does not explicitly state this cannot occur 
            // (newVersion < oldVersion). We cannot downgrade so recreate the database instead.
            Log.w(TAG, "Database downgrade detected; DB downgrade is not supported");
            recreateDatabase(db);
        } else if (oldVersion < 5) {
            Log.w(TAG, "Old database schema detected; pre-v1.0.0, no DB schema backward compatibility is implemented");
            recreateDatabase(db);
        } else try {
            // first migrate from public keys to accounts if necessary
            if (oldVersion == 5) {
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
                        final long accountId = accountRecordsDao.insert(0, publicKey.publicKeyRaw,
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
            } else {
                // oldVersion == 6, add parent id column to accounts table
                db.execSQL("ALTER TABLE " + AccountRecordsSchema.TABLE_ACCOUNTS +
                        " ADD COLUMN " + AccountRecordsSchema.COLUMN_ACCOUNTS_PARENT_ID);
            }

            // migrate to multi account structure
            Log.w(TAG, "Old database schema detected; pre-v2.1.0, migrating to multi account structure");

            // migrate to multi account structure
            // first add parent id column to accounts table
            // add parent ids to accounts table
            try (final Cursor cursor = db.query(AuthorizationsSchema.TABLE_AUTHORIZATIONS,
                    new String[]{
                            AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ID,
                            AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ACCOUNT_ID
                    },
                    null, null, null, null,
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ID)) {
                AccountRecordsDao accountRecordsDao = new AccountRecordsDao(db);
                while (cursor.moveToNext()) {
                    final int parentId = cursor.getInt(0);
                    final int accountId = cursor.getInt(1);
                    ContentValues values = new ContentValues();
                    values.put(AccountRecordsSchema.COLUMN_ACCOUNTS_PARENT_ID, parentId);
                    accountRecordsDao.update(AccountRecordsSchema.TABLE_ACCOUNTS, values,
                            AccountRecordsSchema.COLUMN_ACCOUNTS_ID + "=" + accountId, null);
                }
            }

            // now we can drop the account id column from the authorizations table
            // first backup the existing authorizations table
            String authorizationMigrationTable = "authorizations_backup";
            db.execSQL("CREATE TEMPORARY TABLE " + authorizationMigrationTable + " (" +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ID + " INTEGER NOT NULL PRIMARY KEY," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_IDENTITY_ID + " INTEGER NOT NULL," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ISSUED + " INTEGER NOT NULL," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ACCOUNT_ID + " INTEGER NOT NULL," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID + " INTEGER NOT NULL," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_SCOPE + " BLOB NOT NULL," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_CHAIN + " TEXT NOT NULL)");
            db.execSQL("INSERT INTO " + authorizationMigrationTable + " SELECT " +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ID + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_IDENTITY_ID + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ISSUED + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ACCOUNT_ID + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_SCOPE + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_CHAIN + " FROM " +
                    AuthorizationsSchema.TABLE_AUTHORIZATIONS);

            // now drop the existing authorizations table and recreate it with new schema
            db.execSQL("DROP TABLE " + AuthorizationsSchema.TABLE_AUTHORIZATIONS);
            db.execSQL(AuthorizationsSchema.CREATE_TABLE_AUTHORIZATIONS);
            db.execSQL("INSERT INTO " + AuthorizationsSchema.TABLE_AUTHORIZATIONS + " SELECT " +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ID + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_IDENTITY_ID + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ISSUED + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_SCOPE + "," +
                    AuthorizationsSchema.COLUMN_AUTHORIZATIONS_CHAIN + " FROM " +
                    authorizationMigrationTable);

            db.execSQL("DROP TABLE IF EXISTS " + authorizationMigrationTable);
        } catch (Throwable ignored) {
            Log.w(TAG, "Database migration failed, recreating database");
            recreateDatabase(db);
        }
    }

    private void recreateDatabase(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + IdentityRecordSchema.TABLE_IDENTITIES);
        db.execSQL("DROP TABLE IF EXISTS " + AuthorizationsSchema.TABLE_AUTHORIZATIONS);
        db.execSQL("DROP TABLE IF EXISTS " + PublicKeysSchema.TABLE_PUBLIC_KEYS);
        db.execSQL("DROP TABLE IF EXISTS " + WalletUriBaseSchema.TABLE_WALLET_URI_BASE);
        db.execSQL("DROP TABLE IF EXISTS " + AccountRecordsSchema.TABLE_ACCOUNTS);
        onCreate(db);
    }

    @NonNull
    public static String getDatabaseName(@NonNull AuthIssuerConfig authIssuerConfig) {
        return authIssuerConfig.name + DATABASE_NAME_SUFFIX;
    }
}
