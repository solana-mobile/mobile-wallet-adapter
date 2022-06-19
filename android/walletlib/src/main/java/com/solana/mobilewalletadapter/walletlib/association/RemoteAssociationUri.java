/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.association;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.AssociationContract;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

public class RemoteAssociationUri extends AssociationUri {
    @NonNull
    public final String reflectorHostAuthority;

    public final long reflectorId;

    public RemoteAssociationUri(@NonNull Uri uri) {
        super(uri);
        validate(uri);
        reflectorHostAuthority = parseReflectorHostAuthority(uri);
        reflectorId = parseReflectorId(uri);
    }

    private static void validate(@NonNull Uri uri) {
        if (!uri.getPath().endsWith(AssociationContract.REMOTE_PATH_SUFFIX)) {
            throw new IllegalArgumentException("uri must end with " +
                    AssociationContract.REMOTE_PATH_SUFFIX);
        }
    }

    @NonNull
    @Override
    public Scenario createScenario(@NonNull Context context,
                                   @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                   @NonNull AuthIssuerConfig authIssuerConfig,
                                   @NonNull Scenario.Callbacks callbacks) {
        throw new UnsupportedOperationException("Remote association is not yet implemented");
    }

    @NonNull
    private static String parseReflectorHostAuthority(@NonNull Uri uri) {
        final String reflectorHostAuthority = uri.getQueryParameter(
                AssociationContract.REMOTE_PARAMETER_REFLECTOR_HOST_AUTHORITY);
        if (reflectorHostAuthority == null || reflectorHostAuthority.isEmpty()) {
            throw new IllegalArgumentException("Reflector host authority must be specified");
        }

        return reflectorHostAuthority;
    }

    private static long parseReflectorId(@NonNull Uri uri) {
        final String reflectorIdStr = uri.getQueryParameter(
                AssociationContract.REMOTE_PARAMETER_REFLECTOR_ID);
        if (reflectorIdStr == null) {
            throw new IllegalArgumentException("Reflector ID parameter must be specified");
        }

        final long reflectorId;
        try {
            reflectorId = Long.parseLong(reflectorIdStr, 10);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Reflector ID parameter must be a long", e);
        }

        return reflectorId;
    }
}
