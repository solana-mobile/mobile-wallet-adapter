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
        return buildAccountRecordFromCursor(cursor);
    }

    @Override
    public long insert(@NonNull long parentId,
                       @NonNull byte[] publicKey,
                       @Nullable String accountLabel,
                       @Nullable Uri accountIcon,
                       @Nullable String[] chains,
                       @Nullable String[] features) {
        final ContentValues accountContentValues = new ContentValues(6);
        accountContentValues.put(COLUMN_ACCOUNTS_PARENT_ID, parentId);
        accountContentValues.put(COLUMN_ACCOUNTS_PUBLIC_KEY_RAW, publicKey);
        accountContentValues.put(COLUMN_ACCOUNTS_LABEL, accountLabel);
        accountContentValues.put(COLUMN_ACCOUNTS_ICON, accountIcon != null ? accountIcon.toString() : null);
        accountContentValues.put(COLUMN_ACCOUNTS_CHAINS, chains != null ? serialize(chains) : null);
        accountContentValues.put(COLUMN_ACCOUNTS_FEATURES, features != null ? serialize(features) : null);
        return super.insert(TABLE_ACCOUNTS, accountContentValues);
    }

    @Override
    public long updateParentId(long oldParentId, long newParentId) {
        final ContentValues accountContentValues = new ContentValues(1);
        accountContentValues.put(COLUMN_ACCOUNTS_PARENT_ID, newParentId);
        return super.update(
                TABLE_ACCOUNTS,
                accountContentValues,
                COLUMN_ACCOUNTS_PARENT_ID + "=?",
                new String[] { String.valueOf(oldParentId) }
        );
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

    @Nullable
    @Override
    public AccountRecord query(long parentId, @NonNull byte[] publicKey) {
        final SQLiteDatabase.CursorFactory accountCursorFactory = (db1, masterQuery, editTable, query) -> {
            query.bindBlob(1, publicKey);
            return new SQLiteCursor(masterQuery, editTable, query);
        };
        try (final Cursor cursor = super.queryWithFactory(accountCursorFactory,
                TABLE_ACCOUNTS,
                ACCOUNTS_COLUMNS,
                COLUMN_ACCOUNTS_PUBLIC_KEY_RAW + "=? AND " +
                        COLUMN_ACCOUNTS_PARENT_ID + "=?" + parentId,
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
                        " WHERE " + COLUMN_ACCOUNTS_PARENT_ID + " NOT IN " +
                        "(SELECT DISTINCT " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ID +
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
                                                               @IntRange(from = 1) int parentId,
                                                               @NonNull byte[] publicKeyRaw,
                                                               @Nullable String accountLabel,
                                                               @Nullable String iconStr,
                                                               @Nullable String chainsStr,
                                                               @Nullable String featuresStr) {
        final Uri icon = iconStr != null ? Uri.parse(iconStr) : null;
        final String[] chains = chainsStr != null ? deserialize(chainsStr) : null;
        final String[] features = featuresStr != null ? deserialize(featuresStr) : null;
        return new AccountRecord(id, parentId, publicKeyRaw, accountLabel, icon, chains, features);
    }

    /*package*/ static AccountRecord buildAccountRecordFromCursor(@NonNull Cursor cursor) {
        final int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_ID));
        final int parentId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_PARENT_ID));
        final byte[] publicKey = cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_PUBLIC_KEY_RAW));
        final String accountLabel = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_LABEL));
        final String accountIconStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_ICON));
        final String chainsString = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_CHAINS));
        final String featuresString = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACCOUNTS_FEATURES));
        final Uri accountIcon = accountIconStr != null ? Uri.parse(accountIconStr) : null;
        final String[] chains = chainsString != null ? deserialize(chainsString) : null;
        final String[] features = featuresString != null ? deserialize(featuresString) : null;
        return new AccountRecord(id, parentId, publicKey, accountLabel, accountIcon, chains, features);
    }
}
