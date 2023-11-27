package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AccountRecordsDao extends DbContentProvider<AccountRecord>
        implements AccountRecordsDaoInterface, AccountRecordsSchema {

    public AccountRecordsDao(SQLiteDatabase db) { super(db); }

    @NonNull
    @Override
    protected AccountRecord cursorToEntity(@NonNull Cursor cursor) {
        final int publicKeyId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_ID));
        final byte[] publicKey = cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_PUBLIC_KEY_RAW));
        final String accountLabel = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_LABEL));
        final String accountIconStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_ICON));
        final String chainsString = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_CHAINS));
        final String featuresString = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_FEATURES));
        final Uri accountIcon = Uri.parse(accountIconStr);
        final String[] chains = deserialize(chainsString);
        final String[] features = deserialize(featuresString);
        return new AccountRecord(publicKeyId, publicKey, accountLabel, accountIcon, chains, features);
    }

    @Override
    public long insert(@NonNull byte[] publicKey,
                       @Nullable String accountLabel,
                       @Nullable Uri accountIcon,
                       @Nullable String[] chains,
                       @Nullable String[] features) {
        final ContentValues accountContentValues = new ContentValues(4);
        accountContentValues.put(COLUMN_ACCOUNTS_PUBLIC_KEY_RAW, publicKey);
        accountContentValues.put(COLUMN_ACCOUNTS_LABEL, accountLabel);
        accountContentValues.put(COLUMN_ACCOUNTS_ICON, accountIcon != null ? accountIcon.toString() : null);
        accountContentValues.put(COLUMN_ACCOUNTS_CHAINS, chains != null ? serialize(chains) : null);
        accountContentValues.put(COLUMN_ACCOUNTS_FEATURES, features != null ? serialize(features) : null);
        return super.insert(TABLE_ACCOUNTS, accountContentValues);
    }

    @Nullable
    @Override
    public AccountRecord query(@NonNull byte[] publicKey) {
        final SQLiteDatabase.CursorFactory accountCursorFactory = (db1, masterQuery, editTable, query) -> {
            query.bindBlob(1, publicKey);
            return new SQLiteCursor(masterQuery, editTable, query);
        };
        try (final Cursor cursor = super.queryWithFactory(accountCursorFactory,
                TABLE_ACCOUNTS,
                ACCOUNTS_COLUMNS,
                COLUMN_ACCOUNTS_PUBLIC_KEY_RAW + "=?",
                null)) {
            if (!cursor.moveToNext()) {
                return null;
            }
            return cursorToEntity(cursor);
        }
    }

    @Override
    public void deleteUnreferencedAccounts() {
        final SQLiteStatement deleteUnreferencedAccounts = super.compileStatement(
                "DELETE FROM " + TABLE_ACCOUNTS +
                        " WHERE " + COLUMN_ACCOUNTS_ID + " NOT IN " +
                        "(SELECT DISTINCT " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ACCOUNT_ID +
                        " FROM " + AuthorizationsSchema.TABLE_AUTHORIZATIONS + ')');
        deleteUnreferencedAccounts.executeUpdateDelete();
    }

    // using a long alphanumeric divider reduces the chance of an array element matching the divider
    private static final String ARRAY_DIVIDER = "#a1r2ra5yd2iv1i9der";

    private String serialize(String[] content){ return TextUtils.join(ARRAY_DIVIDER, content); }

    private static String[] deserialize(String content){
        return content.split(ARRAY_DIVIDER);
    }

    /*package*/ static AccountRecord buildAccountRecordFromRaw(@IntRange(from = 1) int id,
                                                               @NonNull byte[] publicKeyRaw,
                                                               @Nullable String accountLabel,
                                                               @Nullable String iconStr,
                                                               @Nullable String chainsStr,
                                                               @Nullable String featuresStr) {
        final Uri icon = iconStr != null ? Uri.parse(iconStr) : null;
        final String[] chains = chainsStr != null ? deserialize(chainsStr) : null;
        final String[] features = featuresStr != null ? deserialize(featuresStr) : null;
        return new AccountRecord(id, publicKeyRaw, accountLabel, icon, chains, features);
    }
}
