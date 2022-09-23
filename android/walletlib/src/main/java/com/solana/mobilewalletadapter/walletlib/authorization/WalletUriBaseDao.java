package com.solana.mobilewalletadapter.walletlib.authorization;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

/*package*/ class WalletUriBaseDao extends DbContentProvider<WalletUri> implements WalletUriBaseDaoInterface, WalletUriBaseSchema {

    WalletUriBaseDao(SQLiteDatabase db) {
        super(db);
    }

    @Override
    protected WalletUri cursorToEntity(Cursor cursor) {
        final int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_WALLET_URI_BASE_ID));
        final String uri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WALLET_URI_BASE_URI));
        return new WalletUri(id, uri);
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
