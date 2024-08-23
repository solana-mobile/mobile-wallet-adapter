/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.transport.websockets;

import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class ReflectorWebSocket implements MessageSender {
    private static final String TAG = ReflectorWebSocket.class.getSimpleName();

    @NonNull
    private final URI mUri;
    @NonNull
    private final MessageReceiver mMessageReceiver;
    private final StateCallbacks mStateCallbacks;
    private final int mConnectTimeoutMs;

    @NonNull
    private State mState = State.NOT_CONNECTED;
    private WebSocketClient mWebSocketClient;

    public ReflectorWebSocket(@NonNull URI uri,
                              @NonNull MessageReceiver messageReceiver,
                              @Nullable StateCallbacks stateCallbacks,
                              @IntRange(from=0) int connectTimeoutMs) {
        Log.v(TAG, "ReflectorWebSocket-ctor");
        mUri = uri;
        mMessageReceiver = messageReceiver;
        mStateCallbacks = stateCallbacks;
        mConnectTimeoutMs = connectTimeoutMs;
    }

    public synchronized void connect() {
        if (mState != State.NOT_CONNECTED) {
            throw new IllegalStateException("connect has already been called for this WebSocket");
        }

        Log.v(TAG, "connect");
        mState = State.CONNECTING;

        try {
            mWebSocketClient = new WebSocketClient(mUri,
                    new Draft_6455(Collections.emptyList(), Collections.singletonList(
                            new Protocol(WebSocketsTransportContract.WEBSOCKETS_PROTOCOL))),
                    null, mConnectTimeoutMs) {
                @Override
                public void onOpen(ServerHandshake handshakeData) {
                    synchronized (ReflectorWebSocket.this) {
                        assert(mState == State.CONNECTING || mState == State.CLOSED);
                        if (mState != State.CONNECTING) {
                            return;
                        }

                        Log.v(TAG, "onConnected");
                        mState = State.CONNECTED;
                        if (mStateCallbacks != null) {
                            mStateCallbacks.onConnected();
                        }
                        mMessageReceiver.receiverConnected(ReflectorWebSocket.this);
                    }
                }

                @Override
                public void onMessage(String message) {
                    synchronized (ReflectorWebSocket.this) {
                        assert(mState == State.CONNECTED || mState == State.CLOSING);
                        if (mState != State.CONNECTED) {
                            return;
                        }

                        Log.v(TAG, "onTextMessage");
                        mMessageReceiver.receiverMessageReceived(message.getBytes(StandardCharsets.UTF_8));
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    synchronized (ReflectorWebSocket.this) {
                        assert(mState == State.CONNECTED || mState == State.CLOSING);
                        if (mState != State.CONNECTED) {
                            return;
                        }

                        Log.v(TAG, "onBinaryMessage");
                        byte[] binary = new byte[bytes.remaining()];
                        bytes.get(binary);
                        mMessageReceiver.receiverMessageReceived(binary);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    synchronized (ReflectorWebSocket.this) {
                        assert(mState == State.CONNECTED || mState == State.CLOSING ||
                                mState == State.CLOSED);
                        if (mState != State.CONNECTED && mState != State.CLOSING) {
                            return;
                        }

                        Log.v(TAG, "onDisconnected");
                        mState = State.CLOSED;
                        mWebSocketClient = null;
                        mMessageReceiver.receiverDisconnected();
                        if (mStateCallbacks != null) {
                            mStateCallbacks.onConnectionClosed();
                        }
                    }
                }

                @Override
                public void onError(Exception ex) {
                    synchronized (ReflectorWebSocket.this) {
                        assert(mState == State.CONNECTING || mState == State.CONNECTED ||
                                mState == State.CLOSING || mState == State.CLOSED);

                        Log.w(TAG, "WebSockets error", ex);
                        switch (mState) {
                            case CONNECTING:
                                mState = State.CLOSED;
                                mWebSocketClient = null;
                                if (mStateCallbacks != null) {
                                    mStateCallbacks.onConnectionFailed();
                                }
                                break;
                            case CONNECTED:
                                mState = State.CLOSING;
                                mWebSocketClient.close();
                                break;
                            case CLOSING:
                                // On an error during closing, just sever the connection
                                mState = State.CLOSED;
                                mWebSocketClient = null;
                                mMessageReceiver.receiverDisconnected();
                                if (mStateCallbacks != null) {
                                    mStateCallbacks.onConnectionClosed();
                                }
                                break;
                            case CLOSED:
                                // No-op; connection closure is already complete
                                break;
                        }
                    }
                }
            };
            mWebSocketClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed creating WebSocket", e);
            mState = State.CLOSED;
            if (mStateCallbacks != null) {
                mStateCallbacks.onConnectionFailed();
            }
        }
    }

    public synchronized void close() {
        Log.v(TAG, "close");
        switch (mState) {
            case NOT_CONNECTED:
            case CONNECTING:
                Log.v(TAG, "closing (before connection established)");
                mState = State.CLOSED;
                mWebSocketClient = null;
                if (mStateCallbacks != null) {
                    mStateCallbacks.onConnectionClosed();
                }
                break;

            case CONNECTED:
                Log.v(TAG, "closing");
                mState = State.CLOSING;
                mWebSocketClient.close();
                break;

            case CLOSING:
            case CLOSED:
                // No-op; closure is either complete, or will complete on its own shortly
                break;
        }
    }

    @Override
    public synchronized void send(@NonNull byte[] message) throws IOException {
        Log.v(TAG, "send");
        if (mState != State.CONNECTED) {
            throw new IOException("Send failed; not connected");
        }
        mWebSocketClient.send(message);
    }

    public interface StateCallbacks {
        /** Invoked when this WebSocket connects successfully to the server */
        void onConnected();

        /** Invoked when this WebSocket fails attempting to connect to the server */
        void onConnectionFailed();

        /**
         * Invoked when this WebSocket connection to the server is terminated.
         * <p>NOTE: this will only be invoked after a previous call to {@link #onConnected()}</p>
         */
        void onConnectionClosed();
    }

    private enum State {
        NOT_CONNECTED, CONNECTING, CONNECTED, CLOSING, CLOSED
    }
}
