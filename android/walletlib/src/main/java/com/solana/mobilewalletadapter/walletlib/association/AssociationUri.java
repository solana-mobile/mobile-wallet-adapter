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
import com.solana.mobilewalletadapter.common.protocol.SessionProperties;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

import java.util.ArrayList;
import java.util.List;

public abstract class AssociationUri {
    @NonNull
    public final Uri uri;

    @NonNull
    public final byte[] associationPublicKey;

    @NonNull
    public final List<SessionProperties.ProtocolVersion> associationProtocolVersions;

    protected AssociationUri(@NonNull Uri uri) {
        this.uri = uri;
        validate(uri);
        associationPublicKey = parseAssociationToken(uri);
        associationProtocolVersions = parseSupportedProtocolVersions(uri);
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

    @NonNull
    private static List<SessionProperties.ProtocolVersion> parseSupportedProtocolVersions(@NonNull Uri uri) {
        final List<SessionProperties.ProtocolVersion> supportedVersions = new ArrayList<>();

        for (String supportVersionStr : uri.getQueryParameters(
                AssociationContract.PARAMETER_PROTOCOL_VERSION)) {
            try {
                supportedVersions.add(SessionProperties.ProtocolVersion.from(supportVersionStr));
            } catch (IllegalArgumentException ignored) {}
        }

        if (supportedVersions.isEmpty()) {
            supportedVersions.add(SessionProperties.ProtocolVersion.LEGACY);
        }

        return supportedVersions;
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
