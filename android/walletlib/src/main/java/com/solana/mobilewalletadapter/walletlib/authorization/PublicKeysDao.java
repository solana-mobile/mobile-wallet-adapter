/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class PublicKeysDao extends DbContentProvider<PublicKey> implements PublicKeysDaoInterface, PublicKeysSchema {

    PublicKeysDao(SQLiteDatabase db) {
        super(db);
    }

    @NonNull
    @Override
    protected PublicKey cursorToEntity(@NonNull Cursor cursor) {
        final int publicKeyId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PUBLIC_KEYS_ID));
        final byte[] publicKey = cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_PUBLIC_KEYS_RAW));
        final String accountLabel = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PUBLIC_KEYS_LABEL));
        return new PublicKey(publicKeyId, publicKey, accountLabel);
    }

    @IntRange(from = -1)
    @Override
    public long insert(@NonNull byte[] publicKey, @Nullable String accountLabel) {
        final ContentValues publicKeyContentValues = new ContentValues(2);
        publicKeyContentValues.put(COLUMN_PUBLIC_KEYS_RAW, publicKey);
        publicKeyContentValues.put(COLUMN_PUBLIC_KEYS_LABEL, accountLabel);
        return super.insert(TABLE_PUBLIC_KEYS, publicKeyContentValues);
    }

    @Nullable
    @Override
    public PublicKey query(@NonNull byte[] publicKey) {
        final SQLiteDatabase.CursorFactory publicKeyCursorFactory = (db1, masterQuery, editTable, query) -> {
            query.bindBlob(1, publicKey);
            return new SQLiteCursor(masterQuery, editTable, query);
        };
        try (final Cursor cursor = super.queryWithFactory(publicKeyCursorFactory,
                TABLE_PUBLIC_KEYS,
                PUBLIC_KEYS_COLUMNS,
                COLUMN_PUBLIC_KEYS_RAW + "=?",
                null)) {
            if (!cursor.moveToNext()) {
                return null;
            }
            return cursorToEntity(cursor);
        }
    }

    @Override
    public void deleteUnreferencedPublicKeys() {
        final SQLiteStatement deleteUnreferencedPublicKeys = super.compileStatement(
                "DELETE FROM " + TABLE_PUBLIC_KEYS +
                        " WHERE " + COLUMN_PUBLIC_KEYS_ID + " NOT IN " +
                        "(SELECT DISTINCT " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                        " FROM " + AuthorizationsSchema.TABLE_AUTHORIZATIONS + ')');
        deleteUnreferencedPublicKeys.executeUpdateDelete();
    }

    @NonNull
    /*package*/ List<PublicKey> getPublicKeys() {
        final ArrayList<PublicKey> publicKeys = new ArrayList<>();
        try (final Cursor c = super.query(TABLE_PUBLIC_KEYS,
                PUBLIC_KEYS_COLUMNS,
                null,
                null,
                COLUMN_PUBLIC_KEYS_ID)) {
            while (c.moveToNext()) {
                publicKeys.add(cursorToEntity(c));
            }
        }
        return publicKeys;
    }
}
