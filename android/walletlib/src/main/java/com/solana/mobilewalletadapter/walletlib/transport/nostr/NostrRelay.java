/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.transport.nostr;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;

import com.solana.mobilewalletadapter.walletlib.transport.websockets.ReflectorWebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NostrRelay implements MessageSender {
    private static final String TAG = NostrRelay.class.getSimpleName();

    @NonNull
    private final URI mRelayUri;
    @NonNull
    private final String mSessionIdentifier;
    @NonNull
    private final String mDappNostrPubkey;
    @NonNull
    private final byte[] mPrivateKey;
    @NonNull
    private final MessageReceiver mMessageReceiver;
    @Nullable
    private final StateCallbacks mStateCallbacks;
    private final int mConnectTimeoutMs;

    @NonNull
    private final String mSubscriptionId = UUID.randomUUID().toString();

    @NonNull
    private State mState = State.NOT_CONNECTED;
    private WebSocketClient mWebSocketClient;

    public NostrRelay(@NonNull URI relayUri,
                      @NonNull String sessionIdentifier,
                      @NonNull String dappNostrPubkey,
                      @NonNull byte[] privateKey,
                      @NonNull MessageReceiver messageReceiver,
                      @Nullable StateCallbacks stateCallbacks,
                      @IntRange(from = 0) int connectTimeoutMs) {
        Log.v(TAG, "NostrRelay-ctor");
        mRelayUri = relayUri;
        mSessionIdentifier = sessionIdentifier;
        mDappNostrPubkey = dappNostrPubkey;
        mPrivateKey = privateKey;
        mMessageReceiver = messageReceiver;
        mStateCallbacks = stateCallbacks;
        mConnectTimeoutMs = connectTimeoutMs;
    }

    public synchronized void connect() {
        if (mState != State.NOT_CONNECTED) {
            throw new IllegalStateException("connect has already been called for this NostrRelay");
        }

        Log.v(TAG, "connect");
        mState = State.CONNECTING;

        try {
            mWebSocketClient = new WebSocketClient(mRelayUri, new Draft_6455(), null, mConnectTimeoutMs) {
                @Override
                public void onOpen(ServerHandshake handshakeData) {
                    synchronized (NostrRelay.this) {
                        if (mState != State.CONNECTING) return;

                        Log.v(TAG, "onOpen — subscribing to session events");
                        mState = State.CONNECTED;
                        doSubscribe();

                        if (mStateCallbacks != null) {
                            mStateCallbacks.onConnected();
                        }
                    }
                }

                @Override
                public void onMessage(String message) {
                    synchronized (NostrRelay.this) {
                        handleRelayMessage(message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    synchronized (NostrRelay.this) {
                        if (mState == State.CLOSED) return;

                        Log.v(TAG, "onClose");
                        if (mState == State.REFLECTION_ESTABLISHED || mState == State.CLOSING) {
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
                    synchronized (NostrRelay.this) {
                        Log.w(TAG, "WebSocket error", ex);
                        switch (mState) {
                            case CONNECTING:
                                mState = State.CLOSED;
                                mWebSocketClient = null;
                                if (mStateCallbacks != null) {
                                    mStateCallbacks.onConnectionFailed();
                                }
                                break;
                            case CONNECTED:
                            case SUBSCRIBED:
                            case REFLECTION_ESTABLISHED:
                                mState = State.CLOSING;
                                mWebSocketClient.close();
                                break;
                            case CLOSING:
                                mState = State.CLOSED;
                                mWebSocketClient = null;
                                mMessageReceiver.receiverDisconnected();
                                if (mStateCallbacks != null) {
                                    mStateCallbacks.onConnectionClosed();
                                }
                                break;
                            case CLOSED:
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
                mState = State.CLOSED;
                mWebSocketClient = null;
                if (mStateCallbacks != null) {
                    mStateCallbacks.onConnectionClosed();
                }
                break;
            case CONNECTED:
            case SUBSCRIBED:
            case REFLECTION_ESTABLISHED:
                mState = State.CLOSING;
                mWebSocketClient.close();
                break;
            case CLOSING:
            case CLOSED:
                break;
        }
    }

    @Override
    public synchronized void send(@NonNull byte[] message) throws IOException {
        Log.d(TAG, "WALLET SEND MESSAGE: " + new String(message));
        if (mState != State.REFLECTION_ESTABLISHED) {
            throw new IOException("Send failed; session not ready");
        }

        sendEvent(message);
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
    }

    private void doSendConnectEvent() {
        sendEvent(new String[][]{{"msg", "CONNECT"}});
    }

    private void doReflectionEstablished() {
        Log.v(TAG, "onReflectionEstablished");
        mState = State.REFLECTION_ESTABLISHED;
        if (mStateCallbacks != null) {
            mStateCallbacks.onReflectionEstablished();
        }
        mMessageReceiver.receiverConnected(this);
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
                    handleOkMessage(msg);
                    break;
                case "EOSE":
                    if (mState == State.CONNECTED) {
                        mState = State.SUBSCRIBED;
                        doSendConnectEvent();
                        doReflectionEstablished();
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
        if (!senderPubkey.equals(mDappNostrPubkey)) {
            Log.w(TAG, "Received event from unexpected pubkey, discarding");
            return;
        }

        String content = event.getString("content");
        if (mState == State.SUBSCRIBED || mState == State.REFLECTION_ESTABLISHED) {
            Map<String, String[]> tags = NostrCrypto.getEventTags(event);
            if (Arrays.toString(tags.get("msg")).contains("SESSION_END")
                    || content.isEmpty()) {
                Log.d(TAG, "Received SESSION_END event from Dapp");
                close();
                return;
            }

            byte[] payload = Base64.decode(content, Base64.DEFAULT);

            mMessageReceiver.receiverMessageReceived(payload);
        }
    }

    private void handleOkMessage(@NonNull JSONArray msg) throws JSONException {
        boolean success = msg.getBoolean(2);
        if (!success) {
            String reason = msg.optString(3, "unknown");
            Log.w(TAG, "Relay rejected event: " + reason);
        }
    }

    private void sendEvent(@NonNull byte[] message) {
        sendEvent(message, new String[0][]);
    }

    private void sendEvent(@NonNull String[][] tags) {
        sendEvent(new byte[0], tags);
    }
    private void sendEvent(@NonNull byte[] message, @NonNull String[][] tags) {
        String[][] fullTags = new String[tags.length + 2][];
        fullTags[0] = new String[]{"d", mSessionIdentifier};
        fullTags[1] = new String[]{"p", mDappNostrPubkey};
        System.arraycopy(tags, 0, fullTags, 2, tags.length);

        String base64Content = Base64.encodeToString(message, Base64.NO_WRAP);
        JSONObject event = NostrCrypto.buildEvent(mPrivateKey,
                NostrCrypto.NOSTR_EVENT_KIND_MWA, base64Content, fullTags);

        JSONArray eventMsg = new JSONArray();
        eventMsg.put("EVENT");
        eventMsg.put(event);
        mWebSocketClient.send(eventMsg.toString());
    }

    public interface StateCallbacks {
        /** Invoked when this WebSocket connects successfully to the server */
        void onConnected();

        /** Invoked when this WebSocket fails attempting to connect to the server */
        void onConnectionFailed();

        /** Invoked when this WebSocket fails attempting to connect to the server */
        void onReflectionEstablished();

        /**
         * Invoked when this WebSocket connection to the server is terminated.
         * <p>NOTE: this will only be invoked after a previous call to {@link #onConnected()}</p>
         */
        void onConnectionClosed();
    }

    private enum State {
        NOT_CONNECTED, CONNECTING, CONNECTED, SUBSCRIBED, REFLECTION_ESTABLISHED, CLOSING, CLOSED
    }
}
