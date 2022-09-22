package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public interface IdentityRecordDaoInterface {
    @NonNull
    List<IdentityRecord> getAuthorizedIdentities();

    @Nullable
    IdentityRecord findIdentityById(String id);

    @Nullable
    IdentityRecord findIdentityByParams(String name, String uri, String relativeIconUri);
}
