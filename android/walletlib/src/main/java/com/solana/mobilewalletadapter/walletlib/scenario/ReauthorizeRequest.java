/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

public class ReauthorizeRequest
        extends BaseVerifiableIdentityRequest<NotifyingCompletableFuture<Boolean>> {
    /*package*/ ReauthorizeRequest(@NonNull NotifyingCompletableFuture<Boolean> request,
                                   @Nullable String identityName,
                                   @Nullable Uri identityUri,
                                   @Nullable Uri iconUri,
                                   @NonNull String cluster,
                                   @NonNull byte[] authorizationScope) {
        super(request, identityName, identityUri, iconUri, cluster, authorizationScope);
    }

    public void completeWithReauthorize() {
        mRequest.complete(true);
    }

    public void completeWithDecline() {
        mRequest.complete(false);
    }
}
