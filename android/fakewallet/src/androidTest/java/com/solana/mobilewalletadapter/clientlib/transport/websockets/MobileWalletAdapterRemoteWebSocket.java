package com.solana.mobilewalletadapter.clientlib.transport.websockets;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;
import com.solana.util.Varint;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public class MobileWalletAdapterRemoteWebSocket implements MessageSender {
    private static final String TAG = MobileWalletAdapterRemoteWebSocket.class.getSimpleName();

    @NonNull
    private final URI mUri;
    @NonNull
    private final MessageReceiver mMessageReceiver;
    private final StateCallbacks mStateCallbacks;
    private final int mConnectTimeoutMs;

    @NonNull
    private State mState = State.NOT_CONNECTED;
    private WebSocketClient mWebSocketClient;

    public MobileWalletAdapterRemoteWebSocket(@NonNull URI uri,
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
                    new Draft_6455(Collections.emptyList(), List.of(
                            new Protocol(WebSocketsTransportContract.WEBSOCKETS_PROTOCOL),
                            new Protocol(WebSocketsTransportContract.WEBSOCKETS_BASE64_PROTOCOL))),
                    null, mConnectTimeoutMs) {
                @Override
                public void onOpen(ServerHandshake handshakeData) {
                    synchronized (this) {
                        assert(mState == State.CONNECTING || mState == State.CLOSED);
                        if (mState != State.CONNECTING) {
                            return;
                        }

                        Log.v(TAG, "onConnected");
                        mState = State.CONNECTED;
                        if (mStateCallbacks != null) {
                            mStateCallbacks.onConnected();
                        }
                    }
                }

                @Override
                public void onMessage(String message) {
                    synchronized (this) {
                        assert(mState == State.CONNECTED || mState == State.REFLECTOR_ID_RECEIVED ||
                                mState == State.REFLECTION_ESTABLISHED || mState == State.CLOSING);
                        if (mState == State.CONNECTED && !message.isEmpty()) {
                            doReflectorIdReceived(Base64.decode(message, Base64.DEFAULT));
                            return;
                        }
                        if (mState == State.REFLECTOR_ID_RECEIVED && message.isEmpty()) {
                            doReflectionEstablished();
                            return;
                        }
                        if (mState != State.REFLECTION_ESTABLISHED) {
                            return;
                        }

                        Log.v(TAG, "onTextMessage");
                        byte[] binary = Base64.decode(message, Base64.DEFAULT);
                        mMessageReceiver.receiverMessageReceived(binary);
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    synchronized (this) {
                        assert(mState == State.CONNECTED || mState == State.REFLECTOR_ID_RECEIVED ||
                                mState == State.REFLECTION_ESTABLISHED || mState == State.CLOSING);
                        if (mState == State.CONNECTED && bytes.hasRemaining()) {
                            doReflectorIdReceived(bytes.array());
                            return;
                        }
                        if (mState == State.REFLECTOR_ID_RECEIVED && !bytes.hasRemaining()) {
                            doReflectionEstablished();
                            return;
                        }
                        if (mState != State.REFLECTION_ESTABLISHED) {
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
                    synchronized (this) {
                        assert(mState == State.CONNECTED || mState == State.REFLECTION_ESTABLISHED ||
                                mState == State.CLOSING || mState == State.CLOSED);
                        if (mState == State.CLOSED) {
                            return;
                        }

                        Log.v(TAG, "onDisconnected");
                        if (mState != State.CONNECTED) {
                            mMessageReceiver.receiverDisconnected();
                        }
                        mState = State.CLOSED;
                        mWebSocketClient = null;
                        if (mStateCallbacks != null) {
                            mStateCallbacks.onConnectionClosed();
                        }
                    }
                }

                @Override
                public void onError(Exception ex) {
                    synchronized (this) {
                        assert(mState == State.CONNECTING || mState == State.REFLECTOR_ID_RECEIVED ||
                                mState == State.REFLECTION_ESTABLISHED || mState == State.CONNECTED ||
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
                            case REFLECTION_ESTABLISHED:
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
            case REFLECTION_ESTABLISHED:
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
        if (mState != State.REFLECTION_ESTABLISHED) {
            throw new IOException("Send failed; reflection not established");
        }
        if (mWebSocketClient.getProtocol().acceptProvidedProtocol(WebSocketsTransportContract.WEBSOCKETS_BASE64_PROTOCOL)) {
            mWebSocketClient.send(Base64.encodeToString(message, Base64.DEFAULT));
        } else {
            mWebSocketClient.send(message);
        }
    }

    private void doReflectorIdReceived(byte[] reflectorIdMessage) {
        int length = (int) Varint.INSTANCE.decode(reflectorIdMessage);
        byte[] reflectorId = new byte[length];
        System.arraycopy(reflectorIdMessage, reflectorIdMessage.length - length,
                reflectorId, 0, length);
        mState = State.REFLECTOR_ID_RECEIVED;
        if (mStateCallbacks != null) {
            mStateCallbacks.onReflectorIdReceived(reflectorId);
        }
    }

    private void doReflectionEstablished() {
        Log.v(TAG, "onReflectionEstablished");
        mState = State.REFLECTION_ESTABLISHED;
        if (mStateCallbacks != null) {
            mStateCallbacks.onReflectionEstablished();
        }
        mMessageReceiver.receiverConnected(this);
    }

    public interface StateCallbacks {
        /** Invoked when this WebSocket connects successfully to the server */
        void onConnected();

        /** Invoked when this WebSocket successfully receives the REFLECTOR_ID message from the server */
        void onReflectorIdReceived(byte[] reflectorId);

        /** Invoked when this WebSocket successfully establishes reflection from the server */
        void onReflectionEstablished();

        /** Invoked when this WebSocket fails attempting to connect to the server */
        void onConnectionFailed();

        /**
         * Invoked when this WebSocket connection to the server is terminated.
         * <p>NOTE: this will only be invoked after a previous call to {@link #onConnected()}</p>
         */
        void onConnectionClosed();
    }

    private enum State {
        NOT_CONNECTED, CONNECTING, CONNECTED,
        REFLECTOR_ID_RECEIVED, REFLECTION_ESTABLISHED,
        CLOSING, CLOSED
    }
}
