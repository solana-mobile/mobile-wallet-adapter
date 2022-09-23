/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*package*/ abstract class DbContentProvider<T> {
    private final SQLiteDatabase mDb;

    /*package*/ DbContentProvider(SQLiteDatabase db) {
        this.mDb = db;
    }

    protected abstract T cursorToEntity(Cursor cursor);

    protected long insert(String tableName, ContentValues values) {
        return mDb.insert(tableName, null, values);
    }

    protected int delete(String tableName, String selection, String[] selectionArgs) {
        return mDb.delete(tableName, selection, selectionArgs);
    }

    protected Cursor query(String tableName, String[] columns,
                        String selection, String[] selectionArgs, String sortOrder) {

        return mDb.query(tableName, columns,
                selection, selectionArgs, null, null, sortOrder);
    }

    protected Cursor query(String tableName, String[] columns,
                        String selection, String[] selectionArgs, String sortOrder,
                        String limit) {

        return mDb.query(tableName, columns, selection,
                selectionArgs, null, null, sortOrder, limit);
    }

    protected Cursor query(String tableName, String[] columns,
                        String selection, String[] selectionArgs, String groupBy,
                        String having, String orderBy, String limit) {

        return mDb.query(tableName, columns, selection,
                selectionArgs, groupBy, having, orderBy, limit);
    }

    protected Cursor queryWithFactory(@NonNull SQLiteDatabase.CursorFactory factory,
                                      @NonNull String tableName,
                                      @NonNull String[] columns,
                                      @Nullable String selection,
                                      @Nullable String[] selectionArgs) {
        return mDb.queryWithFactory(factory,
                false,
                tableName,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                null,
                null);
    }

    protected int update(String tableName, ContentValues values,
                      String selection, String[] selectionArgs) {
        return mDb.update(tableName, values, selection,
                selectionArgs);
    }

    protected Cursor rawQuery(String sql, String[] selectionArgs) {
        return mDb.rawQuery(sql, selectionArgs);
    }

    protected SQLiteStatement compileStatement(String sql) {
        return mDb.compileStatement(sql);
    }
}
