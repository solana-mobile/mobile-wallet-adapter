package com.solana.mobilewalletadapter.walletlib.association;

import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.AssociationContract;

public abstract class ReflectorAssociationUri extends AssociationUri {
    @NonNull
    public final String reflectorHostAuthority;

    @NonNull
    public final byte[] reflectorIdBytes;

    ReflectorAssociationUri(@NonNull Uri uri, @NonNull String remotePathSuffix) {
        super(uri);
        validate(uri, remotePathSuffix);
        reflectorHostAuthority = parseReflectorHostAuthority(uri);
        reflectorIdBytes = parseReflectorId(uri);
    }

    private static void validate(@NonNull Uri uri, @NonNull String remotePathSuffix) {
        if (!uri.getPath().endsWith(remotePathSuffix)) {
            throw new IllegalArgumentException("uri must end with " + remotePathSuffix);
        }
    }

    @NonNull
    private static String parseReflectorHostAuthority(@NonNull Uri uri) {
        final String reflectorHostAuthority = uri.getQueryParameter(
                AssociationContract.PARAMETER_REFLECTOR_HOST_AUTHORITY);
        if (reflectorHostAuthority == null || reflectorHostAuthority.isEmpty()) {
            throw new IllegalArgumentException("Reflector host authority must be specified");
        }

        return reflectorHostAuthority;
    }

    private static byte[] parseReflectorId(@NonNull Uri uri) {
        final String reflectorIdStr = uri.getQueryParameter(
                AssociationContract.PARAMETER_REFLECTOR_ID);
        if (reflectorIdStr == null) {
            throw new IllegalArgumentException("Reflector ID parameter must be specified");
        }

        final byte[] reflectorId;
        try {
            reflectorId = Base64.decode(reflectorIdStr, Base64.URL_SAFE);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Reflector ID parameter must be a base64 url encoded byte sequence", e);
        }

        return reflectorId;
    }
}
