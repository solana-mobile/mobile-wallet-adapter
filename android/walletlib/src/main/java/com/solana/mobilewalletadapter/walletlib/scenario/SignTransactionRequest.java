/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignTransactionRequest extends SignPayloadRequest {
    /*package*/ SignTransactionRequest(@NonNull MobileWalletAdapterServer.SignPayloadRequest request,
                                       @Nullable String identityName,
                                       @Nullable Uri identityUri,
                                       @Nullable Uri iconUri,
                                       @NonNull byte[] authorizationScope,
                                       @NonNull byte[] publicKey) {
        super(request, identityName, identityUri, iconUri, authorizationScope, publicKey);
        if (request.type != MobileWalletAdapterServer.SignPayloadRequest.Type.Transaction) {
            throw new IllegalArgumentException("request should be a Transaction");
        }
    }
}
