/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.transport.websockets.server;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.walletlib.scenario.LocalWebSocketServerScenario;

import fi.iki.elonen.NanoWSD;

import java.io.IOException;
import java.util.Objects;

public class LocalMobileWalletAdapterWebSocketServer extends NanoWSD {
    private static final String TAG = LocalMobileWalletAdapterWebSocketServer.class.getSimpleName();
    private static final int SOCKET_TIMEOUT_MS = 60000;

    @NonNull
    private final LocalWebSocketServerScenario mScenario;
    @NonNull
    private State mState = State.NOT_INITIALIZED;

    public LocalMobileWalletAdapterWebSocketServer(@NonNull LocalWebSocketServerScenario scenario) {
        super("localhost", scenario.port);
        mScenario = scenario;
    }

    public void init() throws IOException {
        if (mState == State.NOT_INITIALIZED) {
            Log.i(TAG, "Starting local mobile-wallet-adapter WebSocket server on port " + mScenario.port);
            mState = State.STARTED;
            start(SOCKET_TIMEOUT_MS);
        } else {
            Log.w(TAG, "Cannot start local mobile-wallet-adapter WebSocket server in " + mState);
        }
    }

    public void close() {
        if (mState == State.STARTED) {
            Log.i(TAG, "Stopping local mobile-wallet-adapter WebSocket server");
            stop();
        }
        mState = State.STOPPED;
    }

    @NonNull
    @Override
    protected WebSocket openWebSocket(@NonNull IHTTPSession handshake) {
        final String protocols = handshake.getHeaders().get(HEADER_WEBSOCKET_PROTOCOL);
        if (protocols != null && arrayContains(protocols.split(","),
                WebSocketsTransportContract.WEBSOCKETS_PROTOCOL)) {
            return new MobileWalletAdapterWebSocket(mScenario, handshake);
        }
        return new NoMatchingProtocolWebSocket(handshake);
    }

    private static <T> boolean arrayContains(@NonNull T[] arr, @Nullable T val) {
        for (T item : arr) {
            if (Objects.equals(item, val)) {
                return true;
            }
        }
        return false;
    }

    private static class NoMatchingProtocolWebSocket extends WebSocket {
        public NoMatchingProtocolWebSocket(@NonNull IHTTPSession handshake) {
            super(handshake);
            getHandshakeResponse().setStatus(Response.Status.BAD_REQUEST);
        }

        @Override
        protected void onOpen() {
            throw new UnsupportedOperationException("Method not implemented");
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason,
                               boolean initiatedByRemote) {
            throw new UnsupportedOperationException("Method not implemented");
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            throw new UnsupportedOperationException("Method not implemented");
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            throw new UnsupportedOperationException("Method not implemented");
        }

        @Override
        protected void onException(IOException exception) {
            throw new UnsupportedOperationException("Method not implemented");
        }
    }

    private enum State {
        NOT_INITIALIZED, STARTED, STOPPED
    }
}