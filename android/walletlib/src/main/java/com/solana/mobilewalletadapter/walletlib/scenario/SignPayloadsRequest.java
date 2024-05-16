/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public abstract class SignPayloadsRequest
        extends BaseVerifiableIdentityRequest<MobileWalletAdapterServer.SignRequest<MobileWalletAdapterServer.SignedPayloadsResult>> {
    @Deprecated
    @NonNull
    protected final byte[] mAuthorizedPublicKey;

    @NonNull
    @Size(min = 1)
    protected final AuthorizedAccount[] mAuthorizedAccounts;

    protected SignPayloadsRequest(@NonNull MobileWalletAdapterServer.SignRequest<MobileWalletAdapterServer.SignedPayloadsResult> request,
                                  @Nullable String identityName,
                                  @Nullable Uri identityUri,
                                  @Nullable Uri iconUri,
                                  @NonNull byte[] authorizationScope,
                                  @NonNull AuthorizedAccount[] authorizedAccounts,
                                  @NonNull String chain) {
        super(request, identityName, identityUri, iconUri, chain, authorizationScope);
        mAuthorizedAccounts = authorizedAccounts;
        mAuthorizedPublicKey = authorizedAccounts[0].publicKey;
    }

    @Deprecated
    protected SignPayloadsRequest(@NonNull MobileWalletAdapterServer.SignRequest<MobileWalletAdapterServer.SignedPayloadsResult> request,
                                  @Nullable String identityName,
                                  @Nullable Uri identityUri,
                                  @Nullable Uri iconUri,
                                  @NonNull byte[] authorizationScope,
                                  @NonNull byte[] authorizedPublicKey,
                                  @NonNull String chain) {
        this(request, identityName, identityUri, iconUri, authorizationScope,
                new AuthorizedAccount[] {
                        new AuthorizedAccount(authorizedPublicKey, null, null, null, null)
                }, chain);
    }

    @Deprecated
    @NonNull
    public byte[] getAuthorizedPublicKey() {
        return mAuthorizedPublicKey;
    }

    @Size(min = 1)
    public AuthorizedAccount[] getAuthorizedAccounts() {
        return mAuthorizedAccounts;
    }

    @NonNull
    @Size(min = 1)
    public byte[][] getPayloads() {
        return mRequest.payloads;
    }

    public void completeWithSignedPayloads(@NonNull @Size(min = 1) byte[][] signedPayloads) {
        mRequest.complete(new MobileWalletAdapterServer.SignedPayloadsResult(signedPayloads));
    }

    public void completeWithDecline() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.RequestDeclinedException(
                "sign request declined"));
    }

    public void completeWithInvalidPayloads(@NonNull @Size(min = 1) boolean[] valid) {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.InvalidPayloadsException(
                "One or more invalid payloads provided", valid));
    }

    public void completeWithTooManyPayloads() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.TooManyPayloadsException(
                "Number of payloads provided for signing exceeds implementation limit"));
    }

    public void completeWithAuthorizationNotValid() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.AuthorizationNotValidException(
                "Current authorization not valid for signing of this payload"));
    }
}
