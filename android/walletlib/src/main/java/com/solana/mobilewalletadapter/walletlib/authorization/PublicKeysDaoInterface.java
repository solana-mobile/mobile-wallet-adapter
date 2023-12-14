/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@Deprecated
/*package*/ interface PublicKeysDaoInterface {

    @IntRange(from = -1)
    long insert(@NonNull byte[] publicKey, @Nullable String accountLabel);

    @Nullable
    PublicKey query(@NonNull byte[] publicKey);

    void deleteUnreferencedPublicKeys();
}
