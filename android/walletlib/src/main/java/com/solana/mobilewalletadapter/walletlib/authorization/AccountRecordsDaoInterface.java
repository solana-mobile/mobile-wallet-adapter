package com.solana.mobilewalletadapter.walletlib.authorization;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*package*/ interface AccountRecordsDaoInterface {

    @IntRange(from = -1)
    long insert(@NonNull byte[] publicKey, @Nullable String accountLabel, @Nullable Uri accountIcon,
                @Nullable String[] chains, @Nullable String[] features);

    @Nullable
    AccountRecord query(@NonNull byte[] publicKey);

    void deleteUnreferencedAccounts();
}
