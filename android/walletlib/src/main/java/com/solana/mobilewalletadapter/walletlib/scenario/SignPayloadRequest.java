/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public abstract class SignPayloadRequest extends BaseVerifiableIdentityRequest {
    @NonNull
    protected final MobileWalletAdapterServer.SignPayloadRequest mRequest;

    @NonNull
    protected final byte[] mPublicKey;

    protected SignPayloadRequest(@NonNull MobileWalletAdapterServer.SignPayloadRequest request,
                                 @Nullable String identityName,
                                 @Nullable Uri identityUri,
                                 @Nullable Uri iconUri,
                                 @NonNull byte[] authorizationScope,
                                 @NonNull byte[] publicKey) {
        super(request, identityName, identityUri, iconUri, authorizationScope);
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

    public void completeWithSignedPayloads(@NonNull @Size(min = 1) byte[][] signedPayloads) {
        mRequest.complete(new MobileWalletAdapterServer.SignedPayloadResult(signedPayloads));
    }

    public void completeWithDecline() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.RequestDeclinedException(
                "sign request declined"));
    }

    public void completeWithInvalidPayloads(@NonNull @Size(min = 1) boolean[] valid) {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.InvalidPayloadException(
                "One or more invalid payloads provided", valid));
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
