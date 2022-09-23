package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

public class WalletUri {
    @IntRange(from = 1)
    final int id;

    @Nullable
    final String uri;

    WalletUri(@IntRange(from = 1) int id, @Nullable String uri) {
        this.id = id;
        this.uri = uri;
    }
}
