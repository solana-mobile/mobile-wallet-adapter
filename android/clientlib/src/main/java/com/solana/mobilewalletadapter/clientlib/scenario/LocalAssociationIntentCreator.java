/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.scenario;

import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.common.AssociationContract;

public class LocalAssociationIntentCreator {

    private LocalAssociationIntentCreator() { }

    @NonNull
    public static Intent createAssociationIntent(Uri endpointPrefix, int port, MobileWalletAdapterSession session) {
        if (endpointPrefix != null && (!endpointPrefix.isAbsolute() || !endpointPrefix.isHierarchical())) {
            throw new IllegalArgumentException("Endpoint-specific URI prefix must be absolute and hierarchical");
        }

        final Uri.Builder dataUriBuilder;

        if (endpointPrefix != null) {
            dataUriBuilder = endpointPrefix.buildUpon()
                    .clearQuery()
                    .fragment(null);
        } else {
            dataUriBuilder = new Uri.Builder()
                    .scheme(AssociationContract.SCHEME_MOBILE_WALLET_ADAPTER);
        }
        final byte[] associationPublicKey = session.getEncodedAssociationPublicKey();
        final String associationToken = Base64.encodeToString(associationPublicKey,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        dataUriBuilder
                .appendEncodedPath(AssociationContract.LOCAL_PATH_SUFFIX)
                .appendQueryParameter(AssociationContract.PARAMETER_ASSOCIATION_TOKEN,
                        associationToken)
                .appendQueryParameter(AssociationContract.LOCAL_PARAMETER_PORT,
                        Integer.toString(port));

        return new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(dataUriBuilder.build());
    }

}
