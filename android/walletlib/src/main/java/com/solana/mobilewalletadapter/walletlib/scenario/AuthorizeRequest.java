/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class AuthorizeRequest
        extends BaseScenarioRequest<NotifyingCompletableFuture<AuthorizeRequest.Result>> {
    private static final String TAG = AuthorizeRequest.class.getSimpleName();

    @Nullable
    protected final String mIdentityName;

    @Nullable
    protected final Uri mIdentityUri;

    @Nullable
    protected final Uri mIconUri;

    @NonNull
    protected final String mChain;

    /*package*/ AuthorizeRequest(@NonNull NotifyingCompletableFuture<Result> request,
                                 @Nullable String identityName,
                                 @Nullable Uri identityUri,
                                 @Nullable Uri iconUri,
                                 @NonNull String chain) {
        super(request);
        mIdentityName = identityName;
        mIdentityUri = identityUri;
        mIconUri = iconUri;
        mChain = chain;
    }

    @Nullable
    public String getIdentityName() {
        return mIdentityName;
    }

    @Nullable
    public Uri getIdentityUri() {
        return mIdentityUri;
    }

    @Nullable
    public Uri getIconRelativeUri() {
        return mIconUri;
    }

    @NonNull @Deprecated
    public String getCluster() {
        return mChain;
    }

    @NonNull
    public String getChain() {
        return mChain;
    }

    @Deprecated
    public void completeWithAuthorize(@NonNull byte[] publicKey,
                                      @Nullable String accountLabel,
                                      @Nullable Uri walletUriBase,
                                      @Nullable byte[] scope) {
        AuthorizedAccount[] accounts = new AuthorizedAccount[] {
                new AuthorizedAccount(publicKey, accountLabel, null, null)};
        mRequest.complete(new Result(accounts, walletUriBase, scope));
    }

    public void completeWithAuthorize(@NonNull AuthorizedAccount[] accounts,
                                      @Nullable Uri walletUriBase,
                                      @Nullable byte[] scope) {
        mRequest.complete(new Result(accounts, walletUriBase, scope));
    }

    public void completeWithDecline() {
        mRequest.complete(null);
    }

    public void completeWithClusterNotSupported() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.ClusterNotSupportedException(
                "Unsupported or invalid cluster specified"));
    }

    /*package*/ static class Result {
        @NonNull @Size(min = 1)
        /*package*/ final AuthorizedAccount[] accounts;
        @Nullable
        /*package*/ final Uri walletUriBase;
        @Nullable
        /*package*/ final byte[] scope;

        private Result(@NonNull @Size(min = 1) AuthorizedAccount[] accounts,
                       @Nullable Uri walletUriBase,
                       @Nullable byte[] scope) {
            this.accounts = accounts;
            this.walletUriBase = walletUriBase;
            this.scope = scope;
        }
    }
}
