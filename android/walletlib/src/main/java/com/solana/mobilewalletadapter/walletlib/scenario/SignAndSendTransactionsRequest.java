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
    @Size(min = 1)
    protected final AuthorizedAccount[] mAuthorizedAccounts;

    /*package*/ SignAndSendTransactionsRequest(
            @NonNull MobileWalletAdapterServer.SignAndSendTransactionsRequest request,
            @Nullable String identityName,
            @Nullable Uri identityUri,
            @Nullable Uri iconUri,
            @NonNull byte[] authorizationScope,
            @NonNull @Size(min = 1) AuthorizedAccount[] authorizedAccounts,
            @NonNull String chain) {
        super(request, identityName, identityUri, iconUri, chain, authorizationScope);
        mAuthorizedAccounts = authorizedAccounts;
    }

    @NonNull
    @Size(min = 1)
    public AuthorizedAccount[] getAuthorizedAccounts() { return mAuthorizedAccounts; }

    @Deprecated
    @NonNull
    public byte[] getPublicKey() {
        return mAuthorizedAccounts[0].publicKey;
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
    public Boolean getWaitForCommitmentToSendNextTransaction() {
        return mRequest.waitForCommitmentToSendNextTransaction;
    }

    @Deprecated
    @Nullable
    public Boolean getWaitForCommitmentYoSendNExtTransaction() {
        return getWaitForCommitmentToSendNextTransaction();
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
