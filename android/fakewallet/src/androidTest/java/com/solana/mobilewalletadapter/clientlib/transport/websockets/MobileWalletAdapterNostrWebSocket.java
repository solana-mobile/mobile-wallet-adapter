package com.solana.mobilewalletadapter.clientlib.transport.websockets;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;
import com.solana.mobilewalletadapter.walletlib.transport.nostr.NostrCrypto;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.*;

public class MobileWalletAdapterNostrWebSocket implements MessageSender {
    private static final String TAG = MobileWalletAdapterNostrWebSocket.class.getSimpleName();

    @NonNull
    private final URI mUri;
    @NonNull
    private final MessageReceiver mMessageReceiver;
    private final StateCallbacks mStateCallbacks;
    private final int mConnectTimeoutMs;

    @NonNull
    private State mState = State.NOT_CONNECTED;
    private WebSocketClient mWebSocketClient;

    @NonNull
    private final String mSubscriptionId = UUID.randomUUID().toString();
    @NonNull
    private final String mSessionIdentifier;
    @NonNull
    private final byte[] mPrivateKey;
    private String mWalletNostrPubkey;

    public MobileWalletAdapterNostrWebSocket(@NonNull URI uri,
                                             @NonNull String sessionIdentifier,
                                             @NonNull byte[] privateKey,
                                             @NonNull MessageReceiver messageReceiver,
                                             @Nullable StateCallbacks stateCallbacks,
                                             @IntRange(from=0) int connectTimeoutMs) {
        Log.v(TAG, "NostrWebSocket-ctor");
        mUri = uri;
        mSessionIdentifier = sessionIdentifier;
        mPrivateKey = privateKey;
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
            mWebSocketClient = new WebSocketClient(mUri, new Draft_6455(), null, mConnectTimeoutMs) {
                @Override
                public void onOpen(ServerHandshake handshakeData) {
                    synchronized (this) {
                        assert(mState == State.CONNECTING || mState == State.CLOSED);
                        if (mState != State.CONNECTING) {
                            return;
                        }

                        Log.v(TAG, "onConnected");
                        mState = State.CONNECTED;
                        doSubscribe();
                        if (mStateCallbacks != null) {
                            mStateCallbacks.onConnected();
                        }
                    }
                }

                @Override
                public void onMessage(String message) {
                    synchronized (this) {
                        assert(mState == State.CONNECTED || mState == State.SUBSCRIBED || mState == State.SUBSCRIBING ||
                                mState == State.REFLECTION_ESTABLISHED || mState == State.CLOSING);

                        Log.v(TAG, "onTextMessage");
                        Log.d(TAG, message);
                        handleRelayMessage(message);
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
                        assert(mState == State.CONNECTING ||
                                mState == State.SUBSCRIBED || mState == State.SUBSCRIBING ||
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
                            case SUBSCRIBING:
                            case SUBSCRIBED:
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

            case REFLECTION_ESTABLISHED:
                doNotifySessionEnd();
            case CONNECTED:
            case SUBSCRIBING:
            case SUBSCRIBED:
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
            throw new IOException("Send failed; session not established");
        }

        String base64Content = Base64.encodeToString(message, Base64.NO_WRAP);
        String[][] tags = mWalletNostrPubkey != null
                ? new String[][]{{"d", mSessionIdentifier}, {"p", mWalletNostrPubkey}}
                : new String[][]{{"d", mSessionIdentifier}};
        JSONObject event = NostrCrypto.buildEvent(mPrivateKey,
                NostrCrypto.NOSTR_EVENT_KIND_MWA, base64Content, tags);

        JSONArray eventMsg = new JSONArray();
        eventMsg.put("EVENT");
        eventMsg.put(event);
        mWebSocketClient.send(eventMsg.toString());
    }

    private void doSubscribe() {
        try {
            JSONObject filter = new JSONObject();
            filter.put("kinds", new JSONArray().put(NostrCrypto.NOSTR_EVENT_KIND_MWA));
            filter.put("#d", new JSONArray().put(mSessionIdentifier));

            JSONArray reqMsg = new JSONArray();
            reqMsg.put("REQ");
            reqMsg.put(mSubscriptionId);
            reqMsg.put(filter);
            mWebSocketClient.send(reqMsg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build REQ message", e);
        }
        mState = State.SUBSCRIBING;
    }

    private void doReflectionEstablished(@NonNull String walletNostrPubkey) {
        Log.v(TAG, "onReflectionEstablished");
        mWalletNostrPubkey = walletNostrPubkey;
        mState = State.REFLECTION_ESTABLISHED;
        if (mStateCallbacks != null) {
            mStateCallbacks.onReflectionEstablished();
        }
        mMessageReceiver.receiverConnected(this);
    }

    private void doNotifySessionEnd() {
        Log.v(TAG, "onNotifySessionEnd");
        String[][] tags = new String[][]{
                {"d", mSessionIdentifier}, {"p", mWalletNostrPubkey}, {"msg", "SESSION_END"}
        };
        JSONObject event = NostrCrypto.buildEvent(mPrivateKey,
                NostrCrypto.NOSTR_EVENT_KIND_MWA, "", tags);

        JSONArray eventMsg = new JSONArray();
        eventMsg.put("EVENT");
        eventMsg.put(event);
        mWebSocketClient.send(eventMsg.toString());
    }

    private void handleRelayMessage(@NonNull String message) {
        try {
            JSONArray msg = new JSONArray(message);
            String type = msg.getString(0);

            switch (type) {
                case "EVENT":
                    handleEventMessage(msg);
                    break;
                case "OK":
                    break;
                case "EOSE":
                    if (mState == State.SUBSCRIBING) {
                        mState = State.SUBSCRIBED;
                        mStateCallbacks.onSubscribed();
                    }
                    break;
                case "NOTICE":
                    Log.d(TAG, "Relay NOTICE: " + msg.optString(1));
                    break;
                case "CLOSED":
                    Log.d(TAG, "Relay CLOSED subscription: " + msg.optString(2));
                    if (mState == State.REFLECTION_ESTABLISHED || mState == State.SUBSCRIBED) {
                        mState = State.CLOSING;
                        mWebSocketClient.close();
                    }
                    break;
                default:
                    Log.d(TAG, "Unknown relay message type: " + type);
                    break;
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse relay message", e);
        }
    }

    private void handleEventMessage(@NonNull JSONArray msg) throws JSONException {
        JSONObject event = msg.getJSONObject(2);
        if (!NostrCrypto.verifyEvent(event)) {
            Log.w(TAG, "Received event with invalid id or signature, discarding");
            return;
        }

        String senderPubkey = event.getString("pubkey");
        String content = event.getString("content");

        switch (mState) {
            case SUBSCRIBED:
                if (content.isEmpty()) {
                    doReflectionEstablished(senderPubkey);
                }
                break;
            case REFLECTION_ESTABLISHED:
                if (mWalletNostrPubkey != null && !senderPubkey.equals(mWalletNostrPubkey)) {
                    Log.w(TAG, "Received event from unexpected pubkey, discarding");
                    return;
                }
                if (content.isEmpty()) return;

                Log.v(TAG, "onTextMessage");
                byte[] payload = Base64.decode(content, Base64.DEFAULT);
                mMessageReceiver.receiverMessageReceived(payload);
                break;
        }
    }

    public interface StateCallbacks {
        /** Invoked when this WebSocket connects successfully to the server */
        void onConnected();

        /** Invoked when this WebSocket has successfully subscribed to the MWA Nostr topic */
        void onSubscribed();

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
        SUBSCRIBING, SUBSCRIBED, REFLECTION_ESTABLISHED,
        CLOSING, CLOSED
    }
}
