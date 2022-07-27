/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.scenario;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.common.AssociationContract;

public class LocalAssociationIntentCreator {

    private LocalAssociationIntentCreator() { }

    @NonNull
    public static Intent createAssociationIntent(@Nullable Uri endpointPrefix,
                                                 @IntRange(from = 0, to = 65535) int port,
                                                 @NonNull MobileWalletAdapterSession session) {
        final byte[] associationPublicKey = session.getEncodedAssociationPublicKey();
        final String associationToken = Base64.encodeToString(associationPublicKey,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        return new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(createAssociationUri(endpointPrefix, port, associationToken));
    }

    public static boolean isWalletEndpointAvailable(@NonNull PackageManager pm) {
        final Intent intent = new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(createAssociationUri(null, 0, ""));
        final ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return (resolveInfo != null);
    }

    @NonNull
    private static Uri createAssociationUri(@Nullable Uri endpointPrefix,
                                            @IntRange(from = 0, to = 65535) int port,
                                            @NonNull String associationToken) {
        if (endpointPrefix != null && (!"https".equals(endpointPrefix.getScheme()) || !endpointPrefix.isHierarchical())) {
            throw new IllegalArgumentException("Endpoint-specific URI prefix must be absolute with scheme 'https' and hierarchical");
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
        return dataUriBuilder
                .appendEncodedPath(AssociationContract.LOCAL_PATH_SUFFIX)
                .appendQueryParameter(AssociationContract.PARAMETER_ASSOCIATION_TOKEN,
                        associationToken)
                .appendQueryParameter(AssociationContract.LOCAL_PARAMETER_PORT,
                        Integer.toString(port))
                .build();
    }
}
