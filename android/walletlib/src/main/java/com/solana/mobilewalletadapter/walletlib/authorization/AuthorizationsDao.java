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
        implements AuthorizationsSchema, PublicKeysSchema,
        WalletUriBaseSchema, AuthorizationsDaoInterface {

    @IntRange(from = 1)
    private final long authorizationValidityMs;

    AuthorizationsDao(SQLiteDatabase db, @IntRange(from = 1) long authorizationValidityMs) {
        super(db);
        this.authorizationValidityMs = authorizationValidityMs;
    }

    @Override
    protected AuthRecord cursorToEntity(Cursor cursor) {
        throw new UnsupportedOperationException("Use cursorToEntity(cursor, identityRecord)");
    }

    private AuthRecord cursorToEntity(Cursor cursor, @NonNull IdentityRecord identityRecord) {
        final int id = cursor.getInt(0);
        final long issued = cursor.getLong(1);
        final int publicKeyId = cursor.getInt(2);
        final int walletUriBaseId = cursor.getInt(3);
        final byte[] scope = cursor.getBlob(4);
        final String cluster = cursor.getString(5);
        final byte[] publicKey = cursor.getBlob(6);
        final String accountLabel = cursor.isNull(7) ? null : cursor.getString(7);
        final Uri walletUriBase = cursor.isNull(8) ? null : Uri.parse(cursor.getString(8));
        return new AuthRecord(id, identityRecord, publicKey,
                accountLabel, cluster, scope, walletUriBase, publicKeyId, walletUriBaseId,
                issued, issued + authorizationValidityMs);
    }

    @IntRange(from = -1)
    @Override
    public long insert(@IntRange(from = 1) int id, long timeStamp, @IntRange(from = 1) int publicKeyId, @NonNull String cluster, @IntRange(from = 1) int walletUriBaseId, @Nullable byte[] scope) {
        final ContentValues contentValues = new ContentValues(6);
        contentValues.put(COLUMN_AUTHORIZATIONS_IDENTITY_ID, id);
        contentValues.put(COLUMN_AUTHORIZATIONS_ISSUED, timeStamp);
        contentValues.put(COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID, publicKeyId);
        contentValues.put(COLUMN_AUTHORIZATIONS_CLUSTER, cluster);
        contentValues.put(COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID, walletUriBaseId);
        contentValues.put(COLUMN_AUTHORIZATIONS_SCOPE, scope);
        return super.insert(TABLE_AUTHORIZATIONS, contentValues);
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
    public synchronized List<AuthRecord> getAuthorizations(@NonNull IdentityRecord identityRecord) {
        final ArrayList<AuthRecord> authorizations = new ArrayList<>();
        try (final Cursor cursor = super.rawQuery("SELECT " +
                        TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_ISSUED +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_SCOPE +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_CLUSTER +
                        ", " + TABLE_PUBLIC_KEYS + '.' + COLUMN_PUBLIC_KEYS_RAW +
                        ", " + TABLE_PUBLIC_KEYS + '.' + COLUMN_PUBLIC_KEYS_LABEL +
                        ", " + TABLE_WALLET_URI_BASE + '.' + COLUMN_WALLET_URI_BASE_URI +
                        " FROM " + TABLE_AUTHORIZATIONS +
                        " INNER JOIN " + TABLE_PUBLIC_KEYS +
                        " ON " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                        " = " + TABLE_PUBLIC_KEYS + '.' + COLUMN_PUBLIC_KEYS_ID +
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
    public AuthRecord getAuthorization(@NonNull IdentityRecord identityRecord, @NonNull String tokenIdStr) {
        try (final Cursor cursor = super.rawQuery("SELECT " +
                        TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_ISSUED +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_SCOPE +
                        ", " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_CLUSTER +
                        ", " + TABLE_PUBLIC_KEYS + '.' + COLUMN_PUBLIC_KEYS_RAW +
                        ", " + TABLE_PUBLIC_KEYS + '.' + COLUMN_PUBLIC_KEYS_LABEL +
                        ", " + TABLE_WALLET_URI_BASE + '.' + COLUMN_WALLET_URI_BASE_URI +
                        " FROM " + TABLE_AUTHORIZATIONS +
                        " INNER JOIN " + TABLE_PUBLIC_KEYS +
                        " ON " + TABLE_AUTHORIZATIONS + '.' + COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                        " = " + TABLE_PUBLIC_KEYS + '.' + COLUMN_PUBLIC_KEYS_ID +
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

    @Override
    public int purgeOldestEntries(@IntRange(from = 1) int identityId, @IntRange(from = 1) int maxOutstandingTokensPerIdentity) {
        final SQLiteStatement purgeOldestStatement = compileStatement(
                "DELETE FROM " + TABLE_AUTHORIZATIONS +
                        " WHERE " + COLUMN_AUTHORIZATIONS_ID + " IN " +
                        "(SELECT " + COLUMN_AUTHORIZATIONS_ID +
                        " FROM " + TABLE_AUTHORIZATIONS +
                        " WHERE " + COLUMN_AUTHORIZATIONS_IDENTITY_ID + "=?" +
                        " ORDER BY " + COLUMN_AUTHORIZATIONS_ISSUED +
                        " DESC LIMIT -1 OFFSET ?)");
        purgeOldestStatement.bindLong(1, identityId);
        purgeOldestStatement.bindLong(2, maxOutstandingTokensPerIdentity);
        return purgeOldestStatement.executeUpdateDelete();    }
}
