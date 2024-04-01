/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignTransactionsRequest extends SignPayloadsRequest {
    /*package*/ SignTransactionsRequest(@NonNull MobileWalletAdapterServer.SignTransactionsRequest request,
                                        @Nullable String identityName,
                                        @Nullable Uri identityUri,
                                        @Nullable Uri iconUri,
                                        @NonNull byte[] authorizationScope,
                                        @NonNull AuthorizedAccount[] authorizedAccounts,
                                        @NonNull String chain) {
        super(request, identityName, identityUri, iconUri, authorizationScope, authorizedAccounts, chain);
    }
}
