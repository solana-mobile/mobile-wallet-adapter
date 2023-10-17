/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

import java.util.Arrays;

public class SignMessagesRequest extends SignPayloadsRequest {
    /*package*/ SignMessagesRequest(@NonNull MobileWalletAdapterServer.SignMessagesRequest request,
                                    @Nullable String identityName,
                                    @Nullable Uri identityUri,
                                    @Nullable Uri iconUri,
                                    @NonNull byte[] authorizationScope,
                                    @NonNull byte[] authorizedPublicKey,
                                    @NonNull String chain) {
        super(request, identityName, identityUri, iconUri, authorizationScope, authorizedPublicKey, chain);

        // TODO(#44): support multiple addresses
        //   this check is temporary; it will become a wallet competency to evaluate the set of
        //   requested addresses once #44 is fixed
        if (request.addresses.length != 1 ||
                !Arrays.equals(request.addresses[0], authorizedPublicKey)) {
            throw new IllegalArgumentException("Authorized public key not requested for signing");
        }
    }
}
