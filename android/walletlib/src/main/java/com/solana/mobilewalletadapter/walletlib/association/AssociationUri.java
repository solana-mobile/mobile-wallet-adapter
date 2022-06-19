/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.association;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.AssociationContract;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

public abstract class AssociationUri {
    @NonNull
    public final Uri uri;

    @NonNull
    public final byte[] associationPublicKey;

    protected AssociationUri(@NonNull Uri uri) {
        this.uri = uri;
        validate(uri);
        associationPublicKey = parseAssociationToken(uri);
    }

    private static void validate(@NonNull Uri uri) {
        if (!uri.isHierarchical()) {
            throw new IllegalArgumentException("uri must be hierarchical");
        }
    }

    @NonNull
    public abstract Scenario createScenario(@NonNull Context context,
                                            @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                            @NonNull AuthIssuerConfig authIssuerConfig,
                                            @NonNull Scenario.Callbacks callbacks);

    @NonNull
    private static byte[] parseAssociationToken(@NonNull Uri uri) {
        final String associationToken = uri.getQueryParameter(
                AssociationContract.PARAMETER_ASSOCIATION_TOKEN);
        if (associationToken == null || associationToken.isEmpty()) {
            throw new IllegalArgumentException("association token must be provided");
        }

        return Base64.decode(associationToken, Base64.URL_SAFE);
    }

    @Nullable
    public static AssociationUri parse(@NonNull Uri uri) {
        try {
            return new LocalAssociationUri(uri);
        } catch (IllegalArgumentException ignored) {}

        try {
            return new RemoteAssociationUri(uri);
        } catch (IllegalArgumentException ignored) {}

        return null;
    }
}
