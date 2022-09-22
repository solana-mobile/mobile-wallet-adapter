package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/*package*/ class AuthorizationsDao extends DbContentProvider<AuthRecord>
        implements AuthorizationsSchema, AuthorizationsDaoInterface {

    AuthorizationsDao(SQLiteDatabase db) {
        super(db);
    }

    @Override
    protected AuthRecord cursorToEntity(Cursor cursor) {
        return null;
    }

    @Override
    public long insert(int id, long timeStamp, int publicKeyId, String cluster, int walletUriBaseId, byte[] scope) {
        final ContentValues reissueContentValues = new ContentValues(6);
        reissueContentValues.put(AuthorizationsSchema.COLUMN_AUTHORIZATIONS_IDENTITY_ID, id);
        reissueContentValues.put(AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ISSUED, timeStamp);
        reissueContentValues.put(AuthorizationsSchema.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID, publicKeyId);
        reissueContentValues.put(AuthorizationsSchema.COLUMN_AUTHORIZATIONS_CLUSTER, cluster);
        reissueContentValues.put(AuthorizationsSchema.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID, walletUriBaseId);
        reissueContentValues.put(AuthorizationsSchema.COLUMN_AUTHORIZATIONS_SCOPE, scope);
        return super.insert(AuthorizationsSchema.TABLE_AUTHORIZATIONS, reissueContentValues);
    }
}
