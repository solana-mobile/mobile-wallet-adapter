/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignMessageRequest extends SignPayloadRequest {
    /*package*/ SignMessageRequest(@NonNull MobileWalletAdapterServer.SignPayloadRequest request,
                                   @NonNull String publicKey) {
        super(request, publicKey);
        if (request.type != MobileWalletAdapterServer.SignPayloadRequest.Type.Message) {
            throw new IllegalArgumentException("request should be a Message");
        }
    }
}
