/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Future;

/*package*/ abstract class BaseVerifiableIdentityRequest extends BaseScenarioRequest
        implements VerifiableIdentityRequest {

    @Nullable
    protected final String mIdentityName;

    @Nullable
    protected final Uri mIdentityUri;

    @Nullable
    protected final Uri mIconUri;

    @NonNull
    protected final String mCluster;

    @NonNull
    protected final byte[] mAuthorizationScope;

    protected BaseVerifiableIdentityRequest(@NonNull Future<?> request,
                                            @Nullable String identityName,
                                            @Nullable Uri identityUri,
                                            @Nullable Uri iconUri,
                                            @NonNull String cluster,
                                            @NonNull byte[] authorizationScope) {
        super(request);
        mIdentityName = identityName;
        mIdentityUri = identityUri;
        mIconUri = iconUri;
        mAuthorizationScope = authorizationScope;
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

    @NonNull
    public byte[] getAuthorizationScope() {
        return mAuthorizationScope;
    }
}
