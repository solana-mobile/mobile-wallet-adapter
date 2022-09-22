package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

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

    @Override
    public int deleteByAuthRecordId(int authRecordId) {
        final SQLiteStatement deleteAuthorizations = compileStatement(
                        "DELETE FROM " + AuthorizationsSchema.TABLE_AUTHORIZATIONS +
                                " WHERE " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_ID + "=?");
        deleteAuthorizations.bindLong(1, authRecordId);
        return deleteAuthorizations.executeUpdateDelete();
    }

    @Override
    public void deleteByIdentityRecordId(int identityRecordId) {
        final SQLiteStatement deleteAuthorizations = compileStatement(
                "DELETE FROM " + AuthorizationsSchema.TABLE_AUTHORIZATIONS +
                        " WHERE " + AuthorizationsSchema.COLUMN_AUTHORIZATIONS_IDENTITY_ID + "=?");
        deleteAuthorizations.bindLong(1, identityRecordId);
        deleteAuthorizations.executeUpdateDelete();
    }
}
