/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

public class SignMessagesRequest extends SignPayloadsRequest {
    @NonNull
    @Size(min = 1)
    protected final byte[][] mAddresses;

    /*package*/ SignMessagesRequest(@NonNull MobileWalletAdapterServer.SignMessagesRequest request,
                                    @Nullable String identityName,
                                    @Nullable Uri identityUri,
                                    @Nullable Uri iconUri,
                                    @NonNull byte[] authorizationScope,
                                    @NonNull AuthorizedAccount[] authorizedAccounts,
                                    @NonNull String chain
    ) {
        super(request, identityName, identityUri, iconUri, authorizationScope, authorizedAccounts, chain);
        this.mAddresses = request.addresses;
    }

    public final byte[][] getAddresses() { return mAddresses; }
}
