/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.transport.websockets.server.LocalMobileWalletAdapterWebSocketServer;

import java.io.IOException;

public class LocalWebSocketServerScenario extends Scenario {
    @WebSocketsTransportContract.LocalPortRange
    public final int port;

    @NonNull
    private final LocalMobileWalletAdapterWebSocketServer mWebSocketServer;
    private State mState = State.NOT_STARTED;

    public LocalWebSocketServerScenario(@NonNull Context context,
                                        @NonNull AuthIssuerConfig authIssuerConfig,
                                        @NonNull Callbacks callbacks,
                                        @NonNull String associationToken,
                                        @WebSocketsTransportContract.LocalPortRange int port) {
        super(context, authIssuerConfig, callbacks, associationToken);
        this.port = port;
        this.mWebSocketServer = new LocalMobileWalletAdapterWebSocketServer(this);
    }

    @Override
    public void start() {
        if (mState != State.NOT_STARTED) {
            throw new IllegalStateException("Already started");
        }
        mState = State.RUNNING;
        try {
            mWebSocketServer.init();
            mIoHandler.post(mCallbacks::onScenarioReady);
        } catch (IOException e) {
            mIoHandler.post(mCallbacks::onScenarioError);
        }
    }

    @Override
    public void close() {
        if (mState == State.CLOSED) {
            return;
        }
        mState = State.CLOSED;
        mIoHandler.post(mCallbacks::onScenarioComplete);
        mWebSocketServer.close(); // this will close all MobileWalletAdapterSessions
        mIoHandler.post(mCallbacks::onScenarioTeardownComplete);
    }

    private enum State {
        NOT_STARTED, RUNNING, CLOSED
    }
}