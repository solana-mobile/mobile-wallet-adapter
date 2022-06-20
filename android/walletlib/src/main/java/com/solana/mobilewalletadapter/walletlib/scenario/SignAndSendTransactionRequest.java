/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;

import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignAndSendTransactionRequest extends ScenarioRequest {
    @NonNull
    private final MobileWalletAdapterServer.SignAndSendTransactionRequest mRequest;

    @NonNull
    protected final String mPublicKey;

    /*package*/ SignAndSendTransactionRequest(
            @NonNull MobileWalletAdapterServer.SignAndSendTransactionRequest request,
            @NonNull String publicKey) {
        super(request);
        mRequest = request;
        mPublicKey = publicKey;
    }

    @NonNull
    public String getPublicKey() {
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

    @Nullable
    public String getCluster() {
        return mRequest.cluster;
    }

    public boolean getSkipPreflight() {
        return mRequest.skipPreflight;
    }

    @NonNull
    public CommitmentLevel getPreflightCommitmentLevel() {
        return mRequest.preflightCommitmentLevel;
    }

    public void completeWithSignatures(@NonNull @Size(min = 1) String[] signatures) {
        mRequest.complete(new MobileWalletAdapterServer.SignatureResult(signatures));
    }

    public void completeWithDecline() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.RequestDeclinedException(
                "sign request declined"));
    }

    public void completeWithInvalidSignatures(@NonNull @Size(min = 1) boolean[] valid) {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.InvalidPayloadException(
                "One or more invalid payloads provided", valid));
    }

    public void completeWithNotCommitted(@NonNull @Size(min = 1) String[] signatures,
                                         @NonNull @Size(min = 1) boolean[] committed) {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.NotCommittedException(
                "One or more transactions did not reach the requested commitment level",
                signatures, committed));
    }

    public void completeWithTooManyPayloads() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.TooManyPayloadsException(
                "Number of payloads provided for signing exceeds implementation limit"));
    }

    @VisibleForTesting
    public void completeWithReauthorizationRequired() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.ReauthorizationRequiredException(
                "auth_token requires reauthorization"));
    }

    @VisibleForTesting
    public void completeWithAuthTokenNotValid() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.AuthTokenNotValidException(
                "auth_token not valid for signing of this payload"));
    }
}
