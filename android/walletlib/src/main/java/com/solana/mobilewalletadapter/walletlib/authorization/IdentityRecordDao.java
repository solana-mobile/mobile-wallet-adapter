package com.solana.mobilewalletadapter.walletlib.authorization;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class IdentityRecordDao extends DbContentProvider<IdentityRecord>
        implements IdentityRecordDaoInterface, IdentityRecordSchema {

    public IdentityRecordDao(SQLiteDatabase db) {
        super(db);
    }

    @Override
    @NonNull
    public List<IdentityRecord> getAuthorizedIdentities() {
        final ArrayList<IdentityRecord> identities = new ArrayList<>();
        try (final Cursor c = super.query(IdentityRecordSchema.TABLE_IDENTITIES,
                IDENTITY_RECORD_COLUMNS,
                null,
                null,
                IdentityRecordSchema.COLUMN_IDENTITIES_NAME)) {
            while (c.moveToNext()) {
                identities.add(cursorToEntity(c));
            }
        }
        return identities;
    }

    @Override
    protected IdentityRecord cursorToEntity(Cursor cursor) {
        final int id = cursor.getInt(0);
        final String name = cursor.getString(1);
        final String uri = cursor.getString(2);
        final String iconRelativeUri = cursor.getString(3);
        final byte[] identityKeyCiphertext = cursor.getBlob(4);
        final byte[] identityKeyIV = cursor.getBlob(5);
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
