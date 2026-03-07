/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.scenario;

import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.common.AssociationContract;
import com.solana.mobilewalletadapter.common.protocol.SessionProperties;

import java.util.Set;

public class ReflectorAssociationIntentCreator {

    private ReflectorAssociationIntentCreator() { }

    @NonNull
    public static Intent createAssociationIntent(@Nullable Uri endpointPrefix,
                                                 @NonNull String hostAuthority,
                                                 @NonNull byte[] reflectorId,
                                                 @NonNull MobileWalletAdapterSession session,
                                                 @NonNull boolean isRemote) {
        final byte[] associationPublicKey = session.getEncodedAssociationPublicKey();
        final String associationToken = Base64.encodeToString(associationPublicKey,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        return new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(createAssociationUri(endpointPrefix, hostAuthority, reflectorId,
                        associationToken, session.getSupportedProtocolVersions(), isRemote));
    }

    @NonNull
    private static Uri createAssociationUri(@Nullable Uri endpointPrefix,
                                            @NonNull String hostAuthority,
                                            @NonNull byte[] reflectorId,
                                            @NonNull String associationToken,
                                            @NonNull Set<SessionProperties.ProtocolVersion> supportedProtocolVersions,
                                            boolean isRemote) {
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

        dataUriBuilder.appendEncodedPath(isRemote ? AssociationContract.REMOTE_PATH_SUFFIX : AssociationContract.LOCAL_REFLECTOR_PATH_SUFFIX)
                .appendQueryParameter(AssociationContract.PARAMETER_ASSOCIATION_TOKEN,
                        associationToken)
                .appendQueryParameter(AssociationContract.PARAMETER_REFLECTOR_HOST_AUTHORITY,
                        hostAuthority)
                .appendQueryParameter(AssociationContract.PARAMETER_REFLECTOR_ID,
                        Base64.encodeToString(reflectorId, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP));

        for (SessionProperties.ProtocolVersion version : supportedProtocolVersions) {
            if (version != SessionProperties.ProtocolVersion.LEGACY)
                dataUriBuilder.appendQueryParameter(
                        AssociationContract.PARAMETER_PROTOCOL_VERSION, version.toString());
        }

        return dataUriBuilder.build();
    }
}
