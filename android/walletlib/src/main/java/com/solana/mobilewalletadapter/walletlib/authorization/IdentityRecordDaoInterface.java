/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/*package*/ interface IdentityRecordDaoInterface {
    @NonNull
    List<IdentityRecord> getAuthorizedIdentities();

    @Nullable
    IdentityRecord findIdentityById(@NonNull String id);

    @Nullable
    IdentityRecord findIdentityByParams(@NonNull String name, @NonNull String uri, @NonNull String relativeIconUri);

    @IntRange(from = -1)
    long insert(@NonNull String name, @NonNull String uri, @NonNull String relativeIconUri, @NonNull byte[] identityKeyCiphertext, @NonNull byte[] identityKeyIV);

    @IntRange(from = -1)
    int deleteById(@IntRange(from = 1) int id);

    void deleteUnreferencedIdentities();
}
