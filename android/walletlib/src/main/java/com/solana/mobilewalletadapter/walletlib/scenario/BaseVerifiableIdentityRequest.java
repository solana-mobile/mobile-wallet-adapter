/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

/*package*/ abstract class BaseVerifiableIdentityRequest<T extends NotifyingCompletableFuture<?>>
        extends BaseScenarioRequest<T>
        implements VerifiableIdentityRequest {

    @Nullable
    protected final String mIdentityName;

    @Nullable
    protected final Uri mIdentityUri;

    @Nullable
    protected final Uri mIconUri;

    @NonNull
    protected final String mChain;

    @NonNull
    protected final byte[] mAuthorizationScope;

    protected BaseVerifiableIdentityRequest(@NonNull T request,
                                            @Nullable String identityName,
                                            @Nullable Uri identityUri,
                                            @Nullable Uri iconUri,
                                            @NonNull String chain,
                                            @NonNull byte[] authorizationScope) {
        super(request);
        mIdentityName = identityName;
        mIdentityUri = identityUri;
        mIconUri = iconUri;
        mAuthorizationScope = authorizationScope;
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

    @NonNull
    public byte[] getAuthorizationScope() {
        return mAuthorizationScope;
    }
}
