/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.transport.websockets.server;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;

import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class MobileWalletAdapterWebSocket extends NanoWSD.WebSocket implements MessageSender {
    private static final String TAG = MobileWalletAdapterWebSocket.class.getSimpleName();

    private static final int PING_FREQUENCY_MS = 45000;

    @NonNull
    private final ScheduledThreadPoolExecutor mPingExecutor;

    @NonNull
    private final Scenario mScenario;
    private MessageReceiver mMessageReceiver;
    @NonNull
    private State mState = State.CONNECTING;

    public MobileWalletAdapterWebSocket(@NonNull Scenario scenario,
                                        @NonNull NanoHTTPD.IHTTPSession handshakeRequest) {
        super(handshakeRequest);

        this.mScenario = scenario;

        // This is used to prepare the response indicating which protocols we support
        handshakeRequest.getHeaders().put(NanoWSD.HEADER_WEBSOCKET_PROTOCOL,
                WebSocketsTransportContract.WEBSOCKETS_PROTOCOL);

        mPingExecutor = new ScheduledThreadPoolExecutor(1);
    }

    @Override
    public synchronized void onOpen() {
        Log.v(TAG, "mobile-wallet-adapter WebSocket opened");
        mState = State.CONNECTED;
        mMessageReceiver = mScenario.createMessageReceiver();
        mMessageReceiver.receiverConnected(this);
        mPingExecutor.scheduleAtFixedRate(this::pingIgnoreIOException,
                PING_FREQUENCY_MS, PING_FREQUENCY_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void onClose(@NonNull NanoWSD.WebSocketFrame.CloseCode code,
                                     @Nullable String reason,
                                     boolean initiatedByRemote) {
        Log.v(TAG, "mobile-wallet-adapter WebSocket closed: " + code + "/" + reason + "/" +
                initiatedByRemote);
        mState = State.CLOSED;
        mPingExecutor.shutdownNow();
        mMessageReceiver.receiverDisconnected();
    }

    @Override
    public synchronized void onMessage(@NonNull NanoWSD.WebSocketFrame message) {
        Log.v(TAG, "onMessage");
        mMessageReceiver.receiverMessageReceived(message.getBinaryPayload());
    }

    @Override
    public void onPong(@NonNull NanoWSD.WebSocketFrame pong) {
    }

    @Override
    public void onException(@NonNull IOException exception) {
        Log.v(TAG, "onException: {}", exception);
    }

    @Override
    public synchronized void send(@NonNull byte[] payload) throws IOException {
        Log.v(TAG, "send");
        super.send(payload);
    }

    public synchronized void close() {
        Log.v(TAG, "close");
        if (mState != State.CLOSING && mState != State.CLOSED) {
            Log.v(TAG, "closing");
            mState = State.CLOSING;
            try {
                close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, null, false);
            } catch (IOException e) {
                Log.e(TAG, "Error while closing WebSocket connection", e);
                mState = State.CLOSED;
            }
        }
    }

    private void pingIgnoreIOException() {
        try {
            ping(new byte[0]);
        } catch (IOException ignored) {}
    }

    private enum State {
        CONNECTING, CONNECTED, CLOSING, CLOSED
    }
}