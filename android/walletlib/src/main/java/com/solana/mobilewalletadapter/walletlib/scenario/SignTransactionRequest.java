package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignTransactionRequest extends SignPayloadRequest {
    /*package*/ SignTransactionRequest(@NonNull MobileWalletAdapterServer.SignPayloadRequest request) {
        super(request);
        if (request.type != MobileWalletAdapterServer.SignPayloadRequest.Type.Transaction) {
            throw new IllegalArgumentException("request should be a Transaction");
        }
    }
}
