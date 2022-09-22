package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.NonNull;

import java.util.List;

public interface IdentityRecordDaoInterface {
    @NonNull
    List<IdentityRecord> getAuthorizedIdentities();
}
