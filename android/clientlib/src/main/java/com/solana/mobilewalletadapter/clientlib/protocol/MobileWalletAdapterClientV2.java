/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.util.Identifier;
import com.solana.mobilewalletadapter.common.util.JsonPack;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class MobileWalletAdapterClientV2 extends MobileWalletAdapterClient {

    public MobileWalletAdapterClientV2(@IntRange(from = 0) int clientTimeoutMs) {
        super(clientTimeoutMs);
    }

    @Deprecated
    @Override
    @NonNull
    public AuthorizationFuture authorize(@Nullable Uri identityUri,
                                         @Nullable Uri iconUri,
                                         @Nullable String identityName,
                                         @Nullable String cluster) throws IOException {
        return authorize(identityUri, iconUri, identityName,
                cluster != null ? Identifier.clusterToChainIdentifier(cluster) : null,
                null, null, null);
    }

    @Override
    @NonNull
    public AuthorizationFuture authorize(@Nullable Uri identityUri,
                                         @Nullable Uri iconUri,
                                         @Nullable String identityName,
                                         @Nullable String chain,
                                         @Nullable String authToken,
                                         @Nullable String[] features,
                                         @Nullable byte[][] addresses
            /* TODO: sign in payload */)
            throws IOException {
        if (identityUri != null && (!identityUri.isAbsolute() || !identityUri.isHierarchical())) {
            throw new IllegalArgumentException("If non-null, identityUri must be an absolute, hierarchical Uri");
        } else if (iconUri != null && !iconUri.isRelative()) {
            throw new IllegalArgumentException("If non-null, iconRelativeUri must be a relative Uri");
        }

        if (chain != null && !Identifier.isValidIdentifier(chain)) {
            throw new IllegalArgumentException("provided chain is not a valid chain identifier");
        }

        final JSONArray featuresArr = features != null ? JsonPack.packStrings(features) : null;
        final JSONArray addressesArr = addresses != null ? JsonPack.packByteArraysToBase64PayloadsArray(addresses) : null;

        final JSONObject authorize;
        try {
            final JSONObject identity = new JSONObject();
            identity.put(ProtocolContract.PARAMETER_IDENTITY_URI, identityUri);
            identity.put(ProtocolContract.PARAMETER_IDENTITY_ICON, iconUri);
            identity.put(ProtocolContract.PARAMETER_IDENTITY_NAME, identityName);
            authorize = new JSONObject();
            authorize.put(ProtocolContract.PARAMETER_IDENTITY, identity);
            authorize.put(ProtocolContract.PARAMETER_CHAIN, chain); // null is OK
            authorize.put(ProtocolContract.PARAMETER_AUTH_TOKEN, authToken); // null is OK
            authorize.put(ProtocolContract.PARAMETER_FEATURES, featuresArr); // null is OK
            authorize.put(ProtocolContract.PARAMETER_ADDRESSES, addressesArr); // null is OK
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create authorize JSON params", e);
        }

        return new AuthorizationFuture(methodCall(ProtocolContract.METHOD_AUTHORIZE, authorize, mClientTimeoutMs));
    }
}