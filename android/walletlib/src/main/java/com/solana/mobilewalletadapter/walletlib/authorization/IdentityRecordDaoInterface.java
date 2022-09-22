/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/*package*/ interface IdentityRecordDaoInterface {
    @NonNull
    List<IdentityRecord> getAuthorizedIdentities();

    @Nullable
    IdentityRecord findIdentityById(String id);

    @Nullable
    IdentityRecord findIdentityByParams(String name, String uri, String relativeIconUri);

    long insert(String name, String uri, String relativeIconUri, byte[] identityKeyCiphertext, byte[] identityKeyIV);
}
