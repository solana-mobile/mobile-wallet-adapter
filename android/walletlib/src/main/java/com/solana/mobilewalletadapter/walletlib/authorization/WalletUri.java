/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

/*package*/ class WalletUri {
    @IntRange(from = 1)
    final int id;

    @Nullable
    final String uri;

    WalletUri(@IntRange(from = 1) int id, @Nullable String uri) {
        this.id = id;
        this.uri = uri;
    }
}
