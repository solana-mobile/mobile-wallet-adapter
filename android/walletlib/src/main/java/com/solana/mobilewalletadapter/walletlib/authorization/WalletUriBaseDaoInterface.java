package com.solana.mobilewalletadapter.walletlib.authorization;

import android.net.Uri;

import androidx.annotation.Nullable;

/*package*/ interface WalletUriBaseDaoInterface {

    long insert(@Nullable Uri uri);

    @Nullable
    WalletUri getByUri(@Nullable Uri uri);

    void deleteUnreferencedWalletUriBase();
}
