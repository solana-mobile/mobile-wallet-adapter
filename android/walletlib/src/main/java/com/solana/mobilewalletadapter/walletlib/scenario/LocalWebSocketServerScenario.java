/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.transport.websockets.server.LocalWebSocketServer;

public class LocalWebSocketServerScenario extends Scenario {
    @WebSocketsTransportContract.LocalPortRange
    public final int port;

    @NonNull
    private final LocalWebSocketServer mWebSocketServer;
    private State mState = State.NOT_STARTED;

    public LocalWebSocketServerScenario(@NonNull Context context,
                                        @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                        @NonNull AuthIssuerConfig authIssuerConfig,
                                        @NonNull Callbacks callbacks,
                                        @NonNull byte[] associationPublicKey,
                                        @WebSocketsTransportContract.LocalPortRange int port) {
        super(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey);
        this.port = port;
        this.mWebSocketServer = new LocalWebSocketServer(this, mWebSocketServerCallbacks);
    }

    @Override
    public void start() {
        if (mState != State.NOT_STARTED) {
            throw new IllegalStateException("Already started");
        }
        mState = State.RUNNING;
        mWebSocketServer.init();
    }

    @Override
    public void close() {
        if (mState == State.CLOSED) {
            return;
        }
        mState = State.CLOSED;
        mIoHandler.post(() -> {
            mCallbacks.onScenarioComplete();
            mWebSocketServer.close(); // this will close all MobileWalletAdapterSessions
            mCallbacks.onScenarioTeardownComplete();
        });
    }

    @NonNull
    private final LocalWebSocketServer.Callbacks mWebSocketServerCallbacks =
            new LocalWebSocketServer.Callbacks() {
        @Override
        public void onStarted() {
            mIoHandler.post(mCallbacks::onScenarioReady);
        }

        @Override
        public void onFatalError() {
            mIoHandler.post(mCallbacks::onScenarioError);
        }
    };

    private enum State {
        NOT_STARTED, RUNNING, CLOSED
    }
}