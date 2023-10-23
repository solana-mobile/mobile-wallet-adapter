/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class DeauthorizedEvent extends BaseVerifiableIdentityRequest<MobileWalletAdapterServer.DeauthorizeRequest> {
    /*package*/ DeauthorizedEvent(@NonNull MobileWalletAdapterServer.DeauthorizeRequest request,
                                  @Nullable String identityName,
                                  @Nullable Uri identityUri,
                                  @Nullable Uri iconUri,
                                  @NonNull String chain,
                                  @NonNull byte[] authorizationScope) {
        super(request, identityName, identityUri, iconUri, chain, authorizationScope);
    }

    public void complete() {
        mRequest.complete(null);
    }
}
