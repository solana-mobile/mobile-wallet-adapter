/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.association;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.AssociationContract;
import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.scenario.LocalScenario;
import com.solana.mobilewalletadapter.walletlib.scenario.LocalWebSocketServerScenario;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

public class LocalAssociationUri extends AssociationUri {
    @WebSocketsTransportContract.LocalPortRange
    public final int port;

    public LocalAssociationUri(@NonNull Uri uri) {
        super(uri);
        validate(uri);
        port = parsePort(uri);
    }

    private static void validate(@NonNull Uri uri) {
        if (!uri.getPath().endsWith(AssociationContract.LOCAL_PATH_SUFFIX)) {
            throw new IllegalArgumentException("uri must end with " +
                    AssociationContract.LOCAL_PATH_SUFFIX);
        }
    }

    @NonNull
    @Override
    public LocalWebSocketServerScenario createScenario(@NonNull Context context,
                                                       @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                                       @NonNull AuthIssuerConfig authIssuerConfig,
                                                       @NonNull Scenario.Callbacks callbacks) {
        if (callbacks instanceof LocalScenario.Callbacks) {
            return new LocalWebSocketServerScenario(context, mobileWalletAdapterConfig,
                    authIssuerConfig, (LocalScenario.Callbacks) callbacks,
                    associationPublicKey, associationProtocolVersions, port);
        } else {
            throw new IllegalArgumentException("callbacks must implement " + LocalScenario.Callbacks.class.getName());
        }
    }

    @WebSocketsTransportContract.LocalPortRange
    private static int parsePort(@NonNull Uri uri) {
        final String portStr = uri.getQueryParameter(AssociationContract.LOCAL_PARAMETER_PORT);
        if (portStr == null) {
            throw new IllegalArgumentException("port parameter must be specified");
        }

        final int port;
        try {
            port = Integer.parseInt(portStr, 10);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("port parameter must be a number", e);
        }

        if (port < WebSocketsTransportContract.WEBSOCKETS_LOCAL_PORT_MIN ||
                port > WebSocketsTransportContract.WEBSOCKETS_LOCAL_PORT_MAX) {
            throw new IllegalArgumentException("port parameter must be between " +
                    WebSocketsTransportContract.WEBSOCKETS_LOCAL_PORT_MIN + " and " +
                    WebSocketsTransportContract.WEBSOCKETS_LOCAL_PORT_MAX);
        }

        return port;
    }
}
