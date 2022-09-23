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

/*package*/ class WalletUriBaseDao extends DbContentProvider<WalletUri> implements WalletUriBaseDaoInterface, WalletUriBaseSchema {

    WalletUriBaseDao(@NonNull SQLiteDatabase db) {
        super(db);
    }

    @NonNull
    @Override
    protected WalletUri cursorToEntity(@NonNull Cursor cursor) {
        final int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_WALLET_URI_BASE_ID));
        final String uri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WALLET_URI_BASE_URI));
        return new WalletUri(id, uri);
    }

    @IntRange(from = -1)
    @Override
    public long insert(@Nullable Uri uri) {
        final ContentValues walletUriBaseContentValues = new ContentValues(1);
        walletUriBaseContentValues.put(COLUMN_WALLET_URI_BASE_URI,
                uri != null ? uri.toString() : null);
        return super.insert(TABLE_WALLET_URI_BASE, walletUriBaseContentValues);
    }

    @Nullable
    @Override
    public WalletUri getByUri(@Nullable Uri uri) {
        try (final Cursor cursor = super.query(TABLE_WALLET_URI_BASE,
                WALLET_URI_BASE_COLUMNS,
                COLUMN_WALLET_URI_BASE_URI + (uri != null ? "=?" : " IS NULL"),
                (uri != null ? new String[]{uri.toString()} : null),
                null)) {
            if (!cursor.moveToNext()) {
                return null;
            }
            return cursorToEntity(cursor);
        }
    }

    @Override
    public void deleteUnreferencedWalletUriBase() {
        final SQLiteStatement deleteUnreferencedWalletUriBase = compileStatement(
                "DELETE FROM " + TABLE_WALLET_URI_BASE +
                        " WHERE " + COLUMN_WALLET_URI_BASE_ID + " NOT IN " +
                        "(SELECT DISTINCT " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                        " FROM " + AuthorizationsSchema.TABLE_AUTHORIZATIONS + ')');
        deleteUnreferencedWalletUriBase.executeUpdateDelete();
    }
}
