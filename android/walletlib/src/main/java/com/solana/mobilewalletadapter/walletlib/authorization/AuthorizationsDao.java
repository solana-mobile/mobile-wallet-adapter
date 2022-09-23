package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/*package*/ class AuthorizationsDao extends DbContentProvider<AuthRecord>
        implements AuthorizationsSchema, PublicKeysSchema,
        WalletUriBaseSchema, AuthorizationsDaoInterface {

    AuthorizationsDao(SQLiteDatabase db) {
        super(db);
    }

    @Override
    protected AuthRecord cursorToEntity(Cursor cursor) {
        return null;
    }

    private AuthRecord cursorToEntity(Cursor cursor, @NonNull IdentityRecord identityRecord, long authorizationValidityMs) {
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

    @Override
    public long insert(int id, long timeStamp, int publicKeyId, String cluster, int walletUriBaseId, byte[] scope) {
        final ContentValues reissueContentValues = new ContentValues(6);
        reissueContentValues.put(COLUMN_AUTHORIZATIONS_IDENTITY_ID, id);
        reissueContentValues.put(COLUMN_AUTHORIZATIONS_ISSUED, timeStamp);
        reissueContentValues.put(COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID, publicKeyId);
        reissueContentValues.put(COLUMN_AUTHORIZATIONS_CLUSTER, cluster);
        reissueContentValues.put(COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID, walletUriBaseId);
        reissueContentValues.put(COLUMN_AUTHORIZATIONS_SCOPE, scope);
        return super.insert(TABLE_AUTHORIZATIONS, reissueContentValues);
    }

    @Override
    public int deleteByAuthRecordId(int authRecordId) {
        final SQLiteStatement deleteAuthorizations = compileStatement(
                "DELETE FROM " + TABLE_AUTHORIZATIONS +
                        " WHERE " + COLUMN_AUTHORIZATIONS_ID + "=?");
        deleteAuthorizations.bindLong(1, authRecordId);
        return deleteAuthorizations.executeUpdateDelete();
    }

    @Override
    public void deleteByIdentityRecordId(int identityRecordId) {
        final SQLiteStatement deleteAuthorizations = compileStatement(
                "DELETE FROM " + TABLE_AUTHORIZATIONS +
                        " WHERE " + COLUMN_AUTHORIZATIONS_IDENTITY_ID + "=?");
        deleteAuthorizations.bindLong(1, identityRecordId);
        deleteAuthorizations.executeUpdateDelete();
    }

    public synchronized List<AuthRecord> getAuthorizations(@NonNull IdentityRecord identityRecord, long authorizationValidityMs) {
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
                authorizations.add(cursorToEntity(cursor, identityRecord, authorizationValidityMs));
            }
        }
        return authorizations;
    }

    @Override
    public AuthRecord getAuthorization(@NonNull IdentityRecord identityRecord, String tokenIdStr, long authorizationValidityMs) {
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

            return cursorToEntity(cursor, identityRecord, authorizationValidityMs);
        }
    }
}
