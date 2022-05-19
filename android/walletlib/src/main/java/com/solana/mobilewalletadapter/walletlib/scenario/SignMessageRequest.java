package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignMessageRequest extends SignPayloadRequest {
    /*package*/ SignMessageRequest(@NonNull MobileWalletAdapterServer.SignPayloadRequest request) {
        super(request);
        if (request.type != MobileWalletAdapterServer.SignPayloadRequest.Type.Message) {
            throw new IllegalArgumentException("request should be a Message");
        }
    }
}
