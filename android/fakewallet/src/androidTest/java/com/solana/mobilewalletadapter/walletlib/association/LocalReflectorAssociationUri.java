package com.solana.mobilewalletadapter.walletlib.association;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.AssociationContract;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.scenario.LocalScenario;
import com.solana.mobilewalletadapter.walletlib.scenario.LocalWebSocketReflectorServerScenario;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

public class LocalReflectorAssociationUri extends ReflectorAssociationUri {

    public LocalReflectorAssociationUri(@NonNull Uri uri) {
        super(uri, AssociationContract.LOCAL_REFLECTOR_PATH_SUFFIX);
    }

    @NonNull
    @Override
    public Scenario createScenario(@NonNull Context context,
                                   @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                   @NonNull AuthIssuerConfig authIssuerConfig,
                                   @NonNull Scenario.Callbacks callbacks) {
        if (callbacks instanceof LocalScenario.Callbacks) {
            return new LocalWebSocketReflectorServerScenario(context, mobileWalletAdapterConfig,
                    authIssuerConfig, (LocalScenario.Callbacks) callbacks, associationPublicKey,
                    associationProtocolVersions, "ws", reflectorHostAuthority, reflectorIdBytes);
        } else {
            throw new IllegalArgumentException("callbacks must implement " + LocalScenario.Callbacks.class.getName());
        }
    }
}
