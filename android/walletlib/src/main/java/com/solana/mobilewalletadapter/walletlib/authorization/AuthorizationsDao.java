/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/*package*/ class AuthorizationsDao extends DbContentProvider<AuthRecord>
        implements AuthorizationsSchema, AccountRecordsSchema,
        WalletUriBaseSchema, AuthorizationsDaoInterface {

    @NonNull
    private final AuthIssuerConfig authIssuerConfig;

    AuthorizationsDao(@NonNull SQLiteDatabase db, @NonNull AuthIssuerConfig authIssuerConfig) {
        super(db);
        this.authIssuerConfig = authIssuerConfig;
    }

    @NonNull
    @Override
    protected AuthRecord cursorToEntity(@NonNull Cursor cursor) {
        throw new UnsupportedOperationException("Use cursorToEntity(cursor, identityRecord)");
    }

    @NonNull
    private AuthRecord cursorToEntity(@NonNull Cursor cursor, @NonNull IdentityRecord identityRecord) {
        final int id = cursor.getInt(0);
        final long issued = cursor.getLong(1);
        final int walletUriBaseId = cursor.getInt(2);
        final byte[] scope = cursor.getBlob(3);
        final String cluster = cursor.getString(4);
        final Uri walletUriBase = cursor.isNull(5) ? null : Uri.parse(cursor.getString(5));
        // look up accounts for this auth record
        final AccountRecord[] accounts = getAccounts(id).toArray(new AccountRecord[0]);
        return new AuthRecord(id, identityRecord, accounts, cluster, scope, walletUriBase,
                walletUriBaseId, issued, issued + authIssuerConfig.authorizationValidityMs);
    }

    @IntRange(from = -1)
    @Override
    public long insert(@IntRange(from = 1) int id, long timeStamp, @NonNull String cluster, @IntRange(from = 1) int walletUriBaseId, @Nullable byte[] scope) {
        final ContentValues contentValues = new ContentValues(5);
        contentValues.put(COLUMN_AUTHORIZATIONS_IDENTITY_ID, id);
        contentValues.put(COLUMN_AUTHORIZATIONS_ISSUED, timeStamp);
        contentValues.put(COLUMN_AUTHORIZATIONS_CHAIN, cluster);
        contentValues.put(COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID, walletUriBaseId);
        contentValues.put(COLUMN_AUTHORIZATIONS_SCOPE, scope);
        return super.insert(TABLE_AUTHORIZATIONS, contentValues);
    }

    @Deprecated
    @IntRange(from = -1)
    @Override
    public long insert(@IntRange(from = 1) int id, long timeStamp, @IntRange(from = 1) int accountId, @NonNull String cluster, @IntRange(from = 1) int walletUriBaseId, @Nullable byte[] scope) {
        return insert(id, timeStamp, cluster, walletUriBaseId, scope);
    }

    @IntRange(from = 0)
    @Override
    public int deleteByAuthRecordId(@IntRange(from = 1) int authRecordId) {
        final SQLiteStatement deleteAuthorizations = compileStatement(
                "DELETE FROM " + TABLE_AUTHORIZATIONS +
                        " WHERE " + COLUMN_AUTHORIZATIONS_ID + "=?");
        deleteAuthorizations.bindLong(1, authRecordId);
        return deleteAuthorizations.executeUpdateDelete();
    }

    @Override
    public void deleteByIdentityRecordId(@IntRange(from = 1) int identityRecordId) {
        final SQLiteStatement deleteAuthorizations = compileStatement(
                "DELETE FROM " + TABLE_AUTHORIZATIONS +
                        " WHERE " + COLUMN_AUTHORIZATIONS_IDENTITY_ID + "=?");
        deleteAuthorizations.bindLong(1, identityRecordId);
        deleteAuthorizations.executeUpdateDelete();
    }

    @NonNull
    private List<AccountRecord> getAccounts(int parentId) {
        final List<AccountRecord> accounts = new ArrayList<>();
        try (final Cursor c = super.query(TABLE_ACCOUNTS,
                ACCOUNTS_COLUMNS,
                COLUMN_ACCOUNTS_PARENT_ID + "=" + parentId,
                null,
                COLUMN_ACCOUNTS_ID)) {
            while (c.moveToNext()) {
                accounts.add(AccountRecordsDao.buildAccountRecordFromCursor(c));
            }
        }
        return accounts;
    }

    @NonNull
    public synchronized List<AuthRecord> getAuthorizations(@NonNull IdentityRecord identityRecord) {
        final ArrayList<AuthRecord> authorizations = new ArrayList<>();
        try (final Cursor cursor = super.rawQuery("SELECT " +
                        TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_ISSUED +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_SCOPE +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_CHAIN +
                        ", " + TABLE_WALLET_URI_BASE + '.' + COLUMN_WALLET_URI_BASE_URI +
                        " FROM " + TABLE_AUTHORIZATIONS +
                        " INNER JOIN " + TABLE_WALLET_URI_BASE +
                        " ON " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                        " = " + TABLE_WALLET_URI_BASE + '.' + COLUMN_WALLET_URI_BASE_ID +
                        " WHERE " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_IDENTITY_ID + "=?",
                new String[]{Integer.toString(identityRecord.getId())})) {
            while (cursor.moveToNext()) {
                authorizations.add(cursorToEntity(cursor, identityRecord));
            }
        }
        return authorizations;
    }

    @Nullable
    @Override
    public AuthRecord getAuthorization(@NonNull IdentityRecord identityRecord,
                                       @NonNull String tokenIdStr) {
        try (final Cursor cursor = super.rawQuery("SELECT " +
                        TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_ISSUED +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_SCOPE +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_CHAIN +
                        ", " + TABLE_WALLET_URI_BASE + '.' + COLUMN_WALLET_URI_BASE_URI +
                        " FROM " + TABLE_AUTHORIZATIONS +
                        " INNER JOIN " + TABLE_WALLET_URI_BASE +
                        " ON " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                        " = " + TABLE_WALLET_URI_BASE + '.' + COLUMN_WALLET_URI_BASE_ID +
                        " WHERE " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_ID + "=?",
                new String[]{tokenIdStr})) {
            if (!cursor.moveToNext()) {
                return null;
            }

            return cursorToEntity(cursor, identityRecord);
        }
    }

    @IntRange(from = 0)
    @Override
    public int purgeOldestEntries(@IntRange(from = 1) int identityId) {
        final SQLiteStatement purgeOldestStatement = compileStatement(
                "DELETE FROM " + TABLE_AUTHORIZATIONS +
                        " WHERE " + COLUMN_AUTHORIZATIONS_ID + " IN " +
                        "(SELECT " + COLUMN_AUTHORIZATIONS_ID +
                        " FROM " + TABLE_AUTHORIZATIONS +
                        " WHERE " + COLUMN_AUTHORIZATIONS_IDENTITY_ID + "=?" +
                        " ORDER BY " + COLUMN_AUTHORIZATIONS_ISSUED +
                        " DESC LIMIT -1 OFFSET ?)");
        purgeOldestStatement.bindLong(1, identityId);
        purgeOldestStatement.bindLong(2, authIssuerConfig.maxOutstandingTokensPerIdentity);
        return purgeOldestStatement.executeUpdateDelete();
    }
}
