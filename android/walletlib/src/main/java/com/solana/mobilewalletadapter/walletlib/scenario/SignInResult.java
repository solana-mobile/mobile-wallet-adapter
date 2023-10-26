package com.solana.mobilewalletadapter.walletlib.scenario;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class SignInResult {
    @NonNull
    public final byte[] publicKey;
    @NonNull
    public final byte[] signedMessage;
    @NonNull
    public final byte[] signature;
    @NonNull
    public final String signatureType;

    public SignInResult(@NonNull byte[] publicKey, @NonNull byte[] signedMessage,
                        @NonNull byte[] signature, @NonNull String signatureType) {
        this.publicKey = publicKey;
        this.signedMessage = signedMessage;
        this.signature = signature;
        this.signatureType = signatureType;
    }

    @NonNull
    @Override
    public String toString() {
        return "SignInResult{" +
                "publicKey=" + Arrays.toString(publicKey) +
                ", signedMessage=" + Arrays.toString(signedMessage) +
                ", signature=" + Arrays.toString(signature) +
                ", signatureType='" + signatureType + '\'' +
                '}';
    }
}
