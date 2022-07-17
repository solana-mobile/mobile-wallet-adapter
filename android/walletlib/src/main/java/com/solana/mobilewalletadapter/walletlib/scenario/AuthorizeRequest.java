/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

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

    /*package*/ AuthorizeRequest(@NonNull NotifyingCompletableFuture<Result> request,
                                 @Nullable String identityName,
                                 @Nullable Uri identityUri,
                                 @Nullable Uri iconUri) {
        super(request);
        mRequest = request;
        mIdentityName = identityName;
        mIdentityUri = identityUri;
        mIconUri = iconUri;
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

    public void completeWithAuthorize(@NonNull byte[] publicKey,
                                      @Nullable Uri walletUriBase,
                                      @Nullable byte[] scope) {
        mRequest.complete(new Result(publicKey, walletUriBase, scope));
    }

    public void completeWithDecline() {
        mRequest.complete(null);
    }

    /*package*/ static class Result {
        @NonNull
        /*package*/ final byte[] publicKey;
        @Nullable
        /*package*/ final Uri walletUriBase;
        @Nullable
        /*package*/ final byte[] scope;

        private Result(@NonNull byte[] publicKey,
                       @Nullable Uri walletUriBase,
                       @Nullable byte[] scope) {
            this.publicKey = publicKey;
            this.walletUriBase = walletUriBase;
            this.scope = scope;
        }
    }
}
