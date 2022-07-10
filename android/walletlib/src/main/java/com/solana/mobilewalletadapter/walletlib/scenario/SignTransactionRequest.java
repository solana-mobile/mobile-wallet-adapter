/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignTransactionRequest extends SignPayloadRequest {
    /*package*/ SignTransactionRequest(@NonNull MobileWalletAdapterServer.SignPayloadRequest request,
                                       @NonNull byte[] publicKey) {
        super(request, publicKey);
        if (request.type != MobileWalletAdapterServer.SignPayloadRequest.Type.Transaction) {
            throw new IllegalArgumentException("request should be a Transaction");
        }
    }
}
