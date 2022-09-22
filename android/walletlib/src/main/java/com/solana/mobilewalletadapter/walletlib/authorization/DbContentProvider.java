package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public abstract class DbContentProvider<T> {
    public SQLiteDatabase mDb;

    public DbContentProvider(SQLiteDatabase db) {
        this.mDb = db;
    }

    protected abstract T cursorToEntity(Cursor cursor);

    public long insert(String tableName, ContentValues values) {
        return mDb.insert(tableName,null, values);
    }

    public int delete(String tableName, String selection, String[] selectionArgs) {
        return mDb.delete(tableName, selection, selectionArgs);
    }

    public Cursor query(String tableName, String[] columns,
                        String selection, String[] selectionArgs, String sortOrder) {

        return mDb.query(tableName, columns,
                selection, selectionArgs, null, null, sortOrder);
    }

    public Cursor query(String tableName, String[] columns,
                        String selection, String[] selectionArgs, String sortOrder,
                        String limit) {

        return mDb.query(tableName, columns, selection,
                selectionArgs, null, null, sortOrder, limit);
    }

    public Cursor query(String tableName, String[] columns,
                        String selection, String[] selectionArgs, String groupBy,
                        String having, String orderBy, String limit) {

        return mDb.query(tableName, columns, selection,
                selectionArgs, groupBy, having, orderBy, limit);
    }

    public int update(String tableName, ContentValues values,
                      String selection, String[] selectionArgs) {
        return mDb.update(tableName, values, selection,
                selectionArgs);
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return mDb.rawQuery(sql, selectionArgs);
    }
}
