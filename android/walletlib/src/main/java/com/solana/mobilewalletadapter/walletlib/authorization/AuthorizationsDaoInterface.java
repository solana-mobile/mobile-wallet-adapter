/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/*package*/ interface AuthorizationsDaoInterface {

    long insert(@IntRange(from = 1) int id, long timeStamp, @IntRange(from = 1) int publicKeyId, @NonNull String cluster, @IntRange(from = 1) int walletUriBaseId, @Nullable byte[] scope);

    int deleteByAuthRecordId(@IntRange(from = 1) int authRecordId);

    void deleteByIdentityRecordId(@IntRange(from = 1) int identityRecordId);

    List<AuthRecord> getAuthorizations(@NonNull IdentityRecord identityRecord);

    AuthRecord getAuthorization(@NonNull IdentityRecord identityRecord, @NonNull String tokenIdStr);
}
