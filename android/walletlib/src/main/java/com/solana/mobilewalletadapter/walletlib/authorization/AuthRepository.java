package com.solana.mobilewalletadapter.walletlib.authorization;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public interface AuthRepository {

    void start();

    void stop();

    @Nullable
    AuthRecord fromAuthToken(@NonNull String authToken);

    @NonNull
    String toAuthToken(@NonNull AuthRecord authRecord);

    @NonNull
    AuthRecord issue(@NonNull String name,
                     @NonNull Uri uri,
                     @NonNull Uri relativeIconUri,
                     @NonNull byte[] publicKey,
                     @Nullable String accountLabel,
                     @NonNull String cluster,
                     @Nullable Uri walletUriBase,
                     @Nullable byte[] scope);

    @Nullable
    AuthRecord reissue(@NonNull AuthRecord authRecord);

    boolean revoke(@NonNull AuthRecord authRecord);

    boolean revoke(@NonNull IdentityRecord identityRecord);

    @NonNull
    List<IdentityRecord> getAuthorizedIdentities();

    @NonNull
    List<AuthRecord> getAuthorizations(@NonNull IdentityRecord identityRecord);
}
