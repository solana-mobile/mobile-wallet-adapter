/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.transport.websockets;

import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketExtension;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class MobileWalletAdapterWebSocket implements MessageSender {
    private static final String TAG = MobileWalletAdapterWebSocket.class.getSimpleName();

    @NonNull
    private final URI mUri;
    @NonNull
    private final MessageReceiver mMessageReceiver;
    private final StateCallbacks mStateCallbacks;
    private final int mConnectTimeoutMs;

    @NonNull
    private State mState = State.NOT_CONNECTED;
    private WebSocket mWebSocket;

    public MobileWalletAdapterWebSocket(@NonNull URI uri,
                                        @NonNull MessageReceiver messageReceiver,
                                        @Nullable StateCallbacks stateCallbacks,
                                        @IntRange(from=0) int connectTimeoutMs) {
        Log.v(TAG, "MobileWalletAdapterWebSocket-ctor");
        mUri = uri;
        mMessageReceiver = messageReceiver;
        mStateCallbacks = stateCallbacks;
        mConnectTimeoutMs = connectTimeoutMs;
    }

    // TODO: use post to move mMessageReceiver calls into a known context
    private final WebSocketListener mListener = new WebSocketAdapter() {
        @Override
        public void onConnected(WebSocket websocket, Map<String, List<String>> headers)
                throws Exception {
            synchronized (MobileWalletAdapterWebSocket.this) {
                assert(mState == State.CONNECTING || mState == State.CLOSED);
                if (mState != State.CONNECTING) {
                    return;
                }

                Log.v(TAG, "onConnected");
                mState = State.CONNECTED;
                if (mStateCallbacks != null) {
                    mStateCallbacks.onConnected();
                }
                mMessageReceiver.receiverConnected(MobileWalletAdapterWebSocket.this);
            }
        }

        @Override
        public void onDisconnected(WebSocket websocket,
                                   WebSocketFrame serverCloseFrame,
                                   WebSocketFrame clientCloseFrame,
                                   boolean closedByServer)
                throws Exception {
            synchronized (MobileWalletAdapterWebSocket.this) {
                assert(mState == State.CONNECTED || mState == State.CLOSING ||
                        mState == State.CLOSED);
                if (mState != State.CONNECTED && mState != State.CLOSING) {
                    return;
                }

                Log.v(TAG, "onDisconnected");
                mState = State.CLOSED;
                mWebSocket = null;
                mMessageReceiver.receiverDisconnected();
                if (mStateCallbacks != null) {
                    mStateCallbacks.onConnectionClosed();
                }
            }
        }

        @Override
        public void onTextMessage(WebSocket websocket, byte[] data) throws Exception {
            synchronized (MobileWalletAdapterWebSocket.this) {
                assert(mState == State.CONNECTED || mState == State.CLOSING);
                if (mState != State.CONNECTED) {
                    return;
                }

                Log.v(TAG, "onTextMessage");
                mMessageReceiver.receiverMessageReceived(data);
            }
        }

        @Override
        public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
            synchronized (MobileWalletAdapterWebSocket.this) {
                assert(mState == State.CONNECTED || mState == State.CLOSING);
                if (mState != State.CONNECTED) {
                    return;
                }

                Log.v(TAG, "onBinaryMessage");
                mMessageReceiver.receiverMessageReceived(binary);
            }
        }

        @Override
        public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
            synchronized (MobileWalletAdapterWebSocket.this) {
                assert(mState == State.CONNECTING || mState == State.CONNECTED ||
                        mState == State.CLOSING || mState == State.CLOSED);

                Log.w(TAG, "WebSockets error", cause);
                switch (mState) {
                    case CONNECTING:
                        mState = State.CLOSED;
                        mWebSocket = null;
                        if (mStateCallbacks != null) {
                            mStateCallbacks.onConnectionFailed();
                        }
                        break;
                    case CONNECTED:
                        mState = State.CLOSING;
                        mWebSocket.disconnect();
                        break;
                    case CLOSING:
                        // On an error during closing, just sever the connection
                        mState = State.CLOSED;
                        mWebSocket = null;
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

    public synchronized void connect() {
        if (mState != State.NOT_CONNECTED) {
            throw new IllegalStateException("connect has already been called for this WebSocket");
        }

        Log.v(TAG, "connect");
        mState = State.CONNECTING;

        try {
            mWebSocket = new WebSocketFactory()
                    .createSocket(mUri, mConnectTimeoutMs)
                    .setDirectTextMessage(true)
                    .addProtocol(WebSocketsTransportContract.WEBSOCKETS_PROTOCOL)
                    .addExtension(WebSocketExtension.PERMESSAGE_DEFLATE)
                    .addListener(mListener);
            mWebSocket.connectAsynchronously();
        } catch (IOException e) {
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
                mWebSocket = null;
                if (mStateCallbacks != null) {
                    mStateCallbacks.onConnectionClosed();
                }
                break;

            case CONNECTED:
                Log.v(TAG, "closing");
                mState = State.CLOSING;
                mWebSocket.disconnect();
                break;

            case CLOSING:
            case CLOSED:
                // No-op; closure is either complete, or will complete on its own shortly
                break;
        }
    }

    @Override
    public synchronized void send(@NonNull byte[] payload) throws IOException {
        Log.v(TAG, "send");
        if (mState != State.CONNECTED) {
            throw new IOException("Send failed; not connected");
        }
        mWebSocket.sendBinary(payload);
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
