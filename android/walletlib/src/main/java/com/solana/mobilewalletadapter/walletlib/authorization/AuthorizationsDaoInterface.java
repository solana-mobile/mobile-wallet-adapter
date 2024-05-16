/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/*package*/ interface AuthorizationsDaoInterface {

    @IntRange(from = -1)
    long insert(@IntRange(from = 1) int id, long timeStamp, @NonNull String cluster, @IntRange(from = 1) int walletUriBaseId, @Nullable byte[] scope);

    @Deprecated
    @IntRange(from = -1)
    long insert(@IntRange(from = 1) int id, long timeStamp, @IntRange(from = 1) int accountId, @NonNull String cluster, @IntRange(from = 1) int walletUriBaseId, @Nullable byte[] scope);

    @IntRange(from = 0)
    int deleteByAuthRecordId(@IntRange(from = 1) int authRecordId);

    void deleteByIdentityRecordId(@IntRange(from = 1) int identityRecordId);

    @NonNull
    List<AuthRecord> getAuthorizations(@NonNull IdentityRecord identityRecord);

    @Nullable
    AuthRecord getAuthorization(@NonNull IdentityRecord identityRecord, @NonNull String tokenIdStr);

    @IntRange(from = 0)
    int purgeOldestEntries(@IntRange(from = 1) int identityId);
}
