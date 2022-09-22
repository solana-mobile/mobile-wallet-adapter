/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/*package*/ abstract class DbContentProvider<T> {
    private final SQLiteDatabase mDb;

    /*package*/ DbContentProvider(SQLiteDatabase db) {
        this.mDb = db;
    }

    /*package*/ abstract T cursorToEntity(Cursor cursor);

    /*package*/ long insert(String tableName, ContentValues values) {
        return mDb.insert(tableName, null, values);
    }

    /*package*/ int delete(String tableName, String selection, String[] selectionArgs) {
        return mDb.delete(tableName, selection, selectionArgs);
    }

    /*package*/ Cursor query(String tableName, String[] columns,
                        String selection, String[] selectionArgs, String sortOrder) {

        return mDb.query(tableName, columns,
                selection, selectionArgs, null, null, sortOrder);
    }

    /*package*/ Cursor query(String tableName, String[] columns,
                        String selection, String[] selectionArgs, String sortOrder,
                        String limit) {

        return mDb.query(tableName, columns, selection,
                selectionArgs, null, null, sortOrder, limit);
    }

    /*package*/ Cursor query(String tableName, String[] columns,
                        String selection, String[] selectionArgs, String groupBy,
                        String having, String orderBy, String limit) {

        return mDb.query(tableName, columns, selection,
                selectionArgs, groupBy, having, orderBy, limit);
    }

    /*package*/ int update(String tableName, ContentValues values,
                      String selection, String[] selectionArgs) {
        return mDb.update(tableName, values, selection,
                selectionArgs);
    }

    /*package*/ Cursor rawQuery(String sql, String[] selectionArgs) {
        return mDb.rawQuery(sql, selectionArgs);
    }
}
