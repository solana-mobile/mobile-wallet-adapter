/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

/*package*/ interface WalletUriBaseDaoInterface {

    @IntRange(from = -1)
    long insert(@Nullable Uri uri);

    @Nullable
    WalletUri getByUri(@Nullable Uri uri);

    void deleteUnreferencedWalletUriBase();
}
