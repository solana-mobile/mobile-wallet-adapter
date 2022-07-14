/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface VerifiableIdentityRequest {
    @Nullable
    String getIdentityName();

    @Nullable
    Uri getIdentityUri();

    @Nullable
    Uri getIconRelativeUri();

    @NonNull
    byte[] getAuthorizationScope();
}
