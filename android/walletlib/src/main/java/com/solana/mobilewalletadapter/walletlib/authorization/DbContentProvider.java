/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*package*/ abstract class DbContentProvider<T> {
    private final SQLiteDatabase mDb;

    /*package*/ DbContentProvider(SQLiteDatabase db) {
        this.mDb = db;
    }

    @NonNull
    protected abstract T cursorToEntity(@NonNull Cursor cursor);

    @IntRange(from = -1)
    protected long insert(@NonNull String tableName, @NonNull ContentValues values) {
        return mDb.insert(tableName, null, values);
    }

    @IntRange(from = 0)
    protected int delete(@NonNull String tableName, @Nullable String selection, @Nullable String[] selectionArgs) {
        return mDb.delete(tableName, selection, selectionArgs);
    }

    @NonNull
    protected Cursor query(@NonNull String tableName, @Nullable String[] columns,
                           @Nullable String selection, @Nullable String[] selectionArgs,
                           @Nullable String sortOrder) {

        return mDb.query(tableName, columns,
                selection, selectionArgs, null, null, sortOrder);
    }

    @NonNull
    protected Cursor query(@NonNull String tableName, @Nullable String[] columns,
                           @Nullable String selection, @Nullable String[] selectionArgs,
                           @Nullable String sortOrder, @Nullable String limit) {

        return mDb.query(tableName, columns, selection,
                selectionArgs, null, null, sortOrder, limit);
    }

    @NonNull
    protected Cursor query(@NonNull String tableName, @Nullable String[] columns,
                           @Nullable String selection, @Nullable String[] selectionArgs,
                           @Nullable String groupBy, @Nullable String having,
                           @Nullable String orderBy, @Nullable String limit) {

        return mDb.query(tableName, columns, selection,
                selectionArgs, groupBy, having, orderBy, limit);
    }

    @NonNull
    protected Cursor queryWithFactory(@NonNull SQLiteDatabase.CursorFactory factory,
                                      @NonNull String tableName,
                                      @Nullable String[] columns,
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

    @IntRange(from = 0)
    protected int update(@NonNull String tableName, @Nullable ContentValues values,
                         @Nullable String selection, @Nullable String[] selectionArgs) {
        return mDb.update(tableName, values, selection, selectionArgs);
    }

    @NonNull
    protected Cursor rawQuery(@NonNull String sql, @Nullable String[] selectionArgs) {
        return mDb.rawQuery(sql, selectionArgs);
    }

    @NonNull
    protected SQLiteStatement compileStatement(@NonNull String sql) {
        return mDb.compileStatement(sql);
    }
}
