/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.NonNull;

import java.util.List;

/*package*/ interface AuthorizationsDaoInterface {

    long insert(int id, long timeStamp, int publicKeyId, String cluster, int walletUriBaseId, byte[] scope);

    int deleteByAuthRecordId(int authRecordId);

    void deleteByIdentityRecordId(int identityRecordId);

    List<AuthRecord> getAuthorizations(@NonNull IdentityRecord identityRecord, long authorizationValidityMs);

    AuthRecord getAuthorization(@NonNull IdentityRecord identityRecord, String tokenIdStr, long authorizationValidityMs);
}
