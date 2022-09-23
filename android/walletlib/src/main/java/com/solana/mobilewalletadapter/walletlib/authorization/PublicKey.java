/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/*package*/ class PublicKey {
    @IntRange(from = 1)
    final int id;

    @NonNull
    final byte[] publicKeyRaw;

    @Nullable
    final String accountLabel;

    PublicKey(@IntRange(from = 1) int id, @NonNull byte[] publicKeyRaw, @Nullable String accountLabel) {
        this.id = id;
        this.publicKeyRaw = publicKeyRaw;
        this.accountLabel = accountLabel;
    }
}
