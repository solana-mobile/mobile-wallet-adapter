/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class AuthorizeRequest extends BaseScenarioRequest {
    private static final String TAG = AuthorizeRequest.class.getSimpleName();

    @NonNull
    private final NotifyingCompletableFuture<Result> mRequest;

    @Nullable
    protected final String mIdentityName;

    @Nullable
    protected final Uri mIdentityUri;

    @Nullable
    protected final Uri mIconUri;

    @NonNull
    protected final String mCluster;

    /*package*/ AuthorizeRequest(@NonNull NotifyingCompletableFuture<Result> request,
                                 @Nullable String identityName,
                                 @Nullable Uri identityUri,
                                 @Nullable Uri iconUri,
                                 @NonNull String cluster) {
        super(request);
        mRequest = request;
        mIdentityName = identityName;
        mIdentityUri = identityUri;
        mIconUri = iconUri;
        mCluster = cluster;
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

    @NonNull
    public String getCluster() {
        return mCluster;
    }

    public void completeWithAuthorize(@NonNull byte[] publicKey,
                                      @Nullable String accountLabel,
                                      @Nullable Uri walletUriBase,
                                      @Nullable byte[] scope) {
        mRequest.complete(new Result(publicKey, accountLabel, walletUriBase, scope));
    }

    public void completeWithDecline() {
        mRequest.complete(null);
    }

    public void completeWithClusterNotSupported() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.ClusterNotSupportedException(
                "Unsupported or invalid cluster specified"));
    }

    /*package*/ static class Result {
        @NonNull
        /*package*/ final byte[] publicKey;
        @Nullable
        /*package*/ final String accountLabel;
        @Nullable
        /*package*/ final Uri walletUriBase;
        @Nullable
        /*package*/ final byte[] scope;

        private Result(@NonNull byte[] publicKey,
                       @Nullable String accountLabel,
                       @Nullable Uri walletUriBase,
                       @Nullable byte[] scope) {
            this.publicKey = publicKey;
            this.accountLabel = accountLabel;
            this.walletUriBase = walletUriBase;
            this.scope = scope;
        }
    }
}
