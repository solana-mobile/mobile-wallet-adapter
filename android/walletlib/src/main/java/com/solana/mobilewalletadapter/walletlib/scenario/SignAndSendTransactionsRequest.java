/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignAndSendTransactionsRequest
        extends BaseVerifiableIdentityRequest<MobileWalletAdapterServer.SignAndSendTransactionsRequest> {
    @NonNull
    protected final byte[] mPublicKey;

    /*package*/ SignAndSendTransactionsRequest(
            @NonNull MobileWalletAdapterServer.SignAndSendTransactionsRequest request,
            @Nullable String identityName,
            @Nullable Uri identityUri,
            @Nullable Uri iconUri,
            @NonNull byte[] authorizationScope,
            @NonNull byte[] publicKey,
            @NonNull String chain) {
        super(request, identityName, identityUri, iconUri, chain, authorizationScope);
        mPublicKey = publicKey;
    }

    @NonNull
    public byte[] getPublicKey() {
        return mPublicKey;
    }

    @NonNull
    @Size(min = 1)
    public byte[][] getPayloads() {
        return mRequest.payloads;
    }

    @Nullable
    public Integer getMinContextSlot() {
        return mRequest.minContextSlot;
    }

    @Nullable
    public String getCommitment() {
        return mRequest.commitment;
    }

    @Nullable
    public Boolean getSkipPreflight() {
        return mRequest.skipPreflight;
    }

    @Nullable
    public Integer getMaxRetries() {
        return mRequest.maxRetries;
    }

    @Nullable
    public Boolean getWaitForCommitmentYoSendNExtTransaction() {
        return mRequest.waitForCommitmentToSendNextTransaction;
    }

    public void completeWithSignatures(@NonNull @Size(min = 1) byte[][] signatures) {
        mRequest.complete(new MobileWalletAdapterServer.SignaturesResult(signatures));
    }

    public void completeWithDecline() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.RequestDeclinedException(
                "sign request declined"));
    }

    public void completeWithInvalidSignatures(@NonNull @Size(min = 1) boolean[] valid) {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.InvalidPayloadsException(
                "One or more invalid payloads provided", valid));
    }

    public void completeWithNotSubmitted(@NonNull @Size(min = 1) byte[][] signatures) {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.NotSubmittedException(
                "One or more transactions were not submitted", signatures));
    }

    public void completeWithTooManyPayloads() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.TooManyPayloadsException(
                "Number of payloads provided for signing exceeds implementation limit"));
    }

    public void completeWithAuthorizationNotValid() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.AuthorizationNotValidException(
                "auth_token not valid for signing of this payload"));
    }
}
