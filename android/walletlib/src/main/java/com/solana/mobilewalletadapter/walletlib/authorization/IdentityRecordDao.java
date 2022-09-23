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

/*package*/ class IdentityRecordDao extends DbContentProvider<IdentityRecord>
        implements IdentityRecordDaoInterface, IdentityRecordSchema {

    public IdentityRecordDao(SQLiteDatabase db) {
        super(db);
    }

    @Override
    @NonNull
    public List<IdentityRecord> getAuthorizedIdentities() {
        final ArrayList<IdentityRecord> identities = new ArrayList<>();
        try (final Cursor c = super.query(TABLE_IDENTITIES,
                IDENTITY_RECORD_COLUMNS,
                null,
                null,
                COLUMN_IDENTITIES_NAME)) {
            while (c.moveToNext()) {
                identities.add(cursorToEntity(c));
            }
        }
        return identities;
    }

    @Override
    @Nullable
    public IdentityRecord findIdentityById(@NonNull String id) {
        try (final Cursor c = super.query(TABLE_IDENTITIES,
                IDENTITY_RECORD_COLUMNS,
                COLUMN_IDENTITIES_ID + "=?",
                new String[]{id},
                null)) {
            if (!c.moveToNext()) {
                return null;
            }

            return cursorToEntity(c);
        }
    }

    @Nullable
    @Override
    public IdentityRecord findIdentityByParams(@NonNull String name, @NonNull String uri, @NonNull String relativeIconUri) {
        try (final Cursor cursor = super.query(TABLE_IDENTITIES,
                IDENTITY_RECORD_COLUMNS,
                COLUMN_IDENTITIES_NAME + "=? AND " +
                        COLUMN_IDENTITIES_URI + "=? AND " +
                        COLUMN_IDENTITIES_ICON_RELATIVE_URI + "=?",
                new String[]{name, uri, relativeIconUri},
                null)) {
            if (!cursor.moveToNext()) {
                return null;
            }
            return cursorToEntity(cursor);
        }
    }

    @IntRange(from = -1)
    @Override
    public long insert(@NonNull String name, @NonNull String uri, @NonNull String relativeIconUri, @NonNull byte[] identityKeyCiphertext, @NonNull byte[] identityKeyIV) {
        final ContentValues identityContentValues = new ContentValues(5);
        identityContentValues.put(COLUMN_IDENTITIES_NAME, name);
        identityContentValues.put(COLUMN_IDENTITIES_URI, uri);
        identityContentValues.put(COLUMN_IDENTITIES_ICON_RELATIVE_URI, relativeIconUri);
        identityContentValues.put(COLUMN_IDENTITIES_SECRET_KEY, identityKeyCiphertext);
        identityContentValues.put(COLUMN_IDENTITIES_SECRET_KEY_IV, identityKeyIV);
        return super.insert(TABLE_IDENTITIES, identityContentValues);
    }

    @IntRange(from = -1)
    @Override
    public int deleteById(int id) {
        final SQLiteStatement deleteIdentity = compileStatement("DELETE FROM " + TABLE_IDENTITIES +
                " WHERE " + COLUMN_IDENTITIES_ID + "=?");
        deleteIdentity.bindLong(1, id);
        return deleteIdentity.executeUpdateDelete();
    }

    @Override
    public void deleteUnreferencedIdentities() {
        final SQLiteStatement deleteUnreferencedIdentities = compileStatement(
                "DELETE FROM " + IdentityRecordSchema.TABLE_IDENTITIES +
                        " WHERE " + IdentityRecordSchema.COLUMN_IDENTITIES_ID + " NOT IN " +
                        "(SELECT DISTINCT " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_IDENTITY_ID +
                        " FROM " + AuthorizationsSchema.TABLE_AUTHORIZATIONS + ')');
        deleteUnreferencedIdentities.executeUpdateDelete();
    }

    @NonNull
    @Override
    protected IdentityRecord cursorToEntity(@NonNull Cursor cursor) {
        final int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IDENTITIES_ID));
        final String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IDENTITIES_NAME));
        final String uri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IDENTITIES_URI));
        final String iconRelativeUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IDENTITIES_ICON_RELATIVE_URI));
        final byte[] identityKeyCiphertext = cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_IDENTITIES_SECRET_KEY));
        final byte[] identityKeyIV = cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_IDENTITIES_SECRET_KEY_IV));
        return new IdentityRecord.IdentityRecordBuilder()
                .setId(id)
                .setName(name)
                .setUri(Uri.parse(uri))
                .setRelativeIconUri(Uri.parse(iconRelativeUri))
                .setSecretKeyCiphertext(identityKeyCiphertext)
                .setSecretKeyIV(identityKeyIV)
                .build();
    }
}
