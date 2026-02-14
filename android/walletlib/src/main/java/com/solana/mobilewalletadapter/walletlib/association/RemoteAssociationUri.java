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
import com.solana.mobilewalletadapter.walletlib.scenario.RemoteWebSocketServerScenario;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

public class RemoteAssociationUri extends ReflectorAssociationUri {
    @Deprecated(forRemoval = true)
    public final long reflectorId = -1;

    public RemoteAssociationUri(@NonNull Uri uri) {
        super(uri, AssociationContract.REMOTE_PATH_SUFFIX);
    }

    @NonNull
    @Override
    public Scenario createScenario(@NonNull Context context,
                                   @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                   @NonNull AuthIssuerConfig authIssuerConfig,
                                   @NonNull Scenario.Callbacks callbacks) {
        return new RemoteWebSocketServerScenario(context, mobileWalletAdapterConfig,
                authIssuerConfig, callbacks, associationPublicKey, reflectorHostAuthority, reflectorIdBytes);
    }
}
