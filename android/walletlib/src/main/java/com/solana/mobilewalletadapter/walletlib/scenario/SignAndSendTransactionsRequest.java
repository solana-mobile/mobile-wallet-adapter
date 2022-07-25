/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignAndSendTransactionsRequest extends BaseVerifiableIdentityRequest {
    @NonNull
    private final MobileWalletAdapterServer.SignAndSendTransactionsRequest mRequest;

    @NonNull
    protected final byte[] mPublicKey;

    /*package*/ SignAndSendTransactionsRequest(
            @NonNull MobileWalletAdapterServer.SignAndSendTransactionsRequest request,
            @Nullable String identityName,
            @Nullable Uri identityUri,
            @Nullable Uri iconUri,
            @NonNull byte[] authorizationScope,
            @NonNull byte[] publicKey,
            @NonNull String cluster) {
        super(request, identityName, identityUri, iconUri, cluster, authorizationScope);
        mRequest = request;
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

    @NonNull
    public CommitmentLevel getCommitmentLevel() {
        return mRequest.commitmentLevel;
    }

    public boolean getSkipPreflight() {
        return mRequest.skipPreflight;
    }

    @NonNull
    public CommitmentLevel getPreflightCommitmentLevel() {
        return mRequest.preflightCommitmentLevel;
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

    public void completeWithNotCommitted(@NonNull @Size(min = 1) byte[][] signatures,
                                         @NonNull @Size(min = 1) boolean[] committed) {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.NotCommittedException(
                "One or more transactions did not reach the requested commitment level",
                signatures, committed));
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
