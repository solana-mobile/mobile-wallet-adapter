package com.solana.mobilewalletadapter.walletlib.authorization;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/*package*/ interface AccountRecordsDaoInterface {

    @IntRange(from = -1)
    long insert(@NonNull long parentId, @NonNull byte[] publicKey,
                @Nullable String accountLabel, @Nullable Uri accountIcon,
                @Nullable String[] chains, @Nullable String[] features);

    @IntRange(from = 0)
    long updateParentId(long oldParentId, long newParentId);

    @Nullable
    AccountRecord query(@NonNull byte[] publicKey);

    @Nullable
    AccountRecord query(long parentId, @NonNull byte[] publicKey);

    void deleteUnreferencedAccounts();
}
