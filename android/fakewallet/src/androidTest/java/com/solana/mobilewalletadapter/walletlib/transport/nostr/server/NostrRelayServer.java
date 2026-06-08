/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.transport.nostr.server;

import android.util.Log;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.walletlib.transport.websockets.server.WebSocketReflectorServer;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * A minimal NIP-01 compliant Nostr relay for integration testing.
 * Supports ephemeral events (kind 20000-29999), REQ subscriptions with #d tag filter,
 * and forwards matching EVENTs to subscribers.
 */
public class NostrRelayServer extends WebSocketServer {
    private static final String TAG = NostrRelayServer.class.getSimpleName();
    private static final int CLOSE_TIME_MS = 5000; // allow 5s for connections to close cleanly, then terminate them

    @NonNull
    private State mState = State.NOT_INITIALIZED;

    // subscriptionId -> Subscription
    @NonNull
    private final Map<String, Subscription> mSubscriptions = new HashMap<>();

    public NostrRelayServer(int port) {
        // Create a WebSocket server on localhost:${port}, with 1 decoding thread,
        super(new InetSocketAddress(WebSocketsTransportContract.WEBSOCKETS_LOCAL_HOST, port), 1,
                Collections.singletonList(new Draft_6455()));
    }

    public void init() {
        if (mState == State.NOT_INITIALIZED) {
            Log.i(TAG, "Starting mock Nostr relay on port " + getPort());
            mState = State.STARTED;
            start();
        }
    }

    public void close() {
        if (mState == State.STARTED) {
            Log.i(TAG, "Stopping mock Nostr relay");
            try {
                stop(CLOSE_TIME_MS, "Relay shutting down");
            } catch (InterruptedException ignored) {}
        }
        mState = State.STOPPED;
    }

    @Override
    public void onStart() {
        Log.d(TAG, "Mock Nostr relay started");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.d(TAG, "Client connected");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "Client disconnected");
        synchronized (mSubscriptions) {
            mSubscriptions.entrySet().removeIf(e -> e.getValue().conn == conn);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JSONArray msg = new JSONArray(message);
            String type = msg.getString(0);

            switch (type) {
                case "REQ":
                    handleReq(conn, msg);
                    break;
                case "EVENT":
                    handleEvent(conn, msg);
                    break;
                case "CLOSE":
                    handleCloseSubscription(msg);
                    break;
                default:
                    Log.d(TAG, "Unknown message type: " + type);
                    break;
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse message", e);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn == null) {
            Log.e(TAG, "Relay FATAL exception", ex);
            close();
        } else {
            Log.w(TAG, "Relay exception", ex);
        }
    }

    private void handleReq(WebSocket conn, JSONArray msg) throws JSONException {
        String subscriptionId = msg.getString(1);
        JSONObject filter = msg.getJSONObject(2);

        List<String> dTagValues = new ArrayList<>();
        if (filter.has("#d")) {
            JSONArray dTags = filter.getJSONArray("#d");
            for (int i = 0; i < dTags.length(); i++) {
                dTagValues.add(dTags.getString(i));
            }
        }

        List<Integer> kinds = new ArrayList<>();
        if (filter.has("kinds")) {
            JSONArray kindsArr = filter.getJSONArray("kinds");
            for (int i = 0; i < kindsArr.length(); i++) {
                kinds.add(kindsArr.getInt(i));
            }
        }

        synchronized (mSubscriptions) {
            mSubscriptions.put(subscriptionId, new Subscription(conn, subscriptionId, kinds, dTagValues));
        }

        Log.d(TAG, "REQ subscription: " + subscriptionId + " dTags=" + dTagValues + " kinds=" + kinds);

        // Send EOSE immediately (no stored events for ephemeral relay)
        JSONArray eose = new JSONArray();
        eose.put("EOSE");
        eose.put(subscriptionId);
        conn.send(eose.toString());
    }

    private void handleEvent(WebSocket sender, JSONArray msg) throws JSONException {
        JSONObject event = msg.getJSONObject(1);
        String eventId = event.getString("id");
        int kind = event.getInt("kind");

        // Send OK to the sender
        JSONArray ok = new JSONArray();
        ok.put("OK");
        ok.put(eventId);
        ok.put(true);
        ok.put("");
        sender.send(ok.toString());

        // Extract d tag value from event
        String dTagValue = null;
        JSONArray tags = event.getJSONArray("tags");
        for (int i = 0; i < tags.length(); i++) {
            JSONArray tag = tags.getJSONArray(i);
            if (tag.length() >= 2 && "d".equals(tag.getString(0))) {
                dTagValue = tag.getString(1);
                break;
            }
        }

        // Forward to matching subscriptions (excluding sender)
        synchronized (mSubscriptions) {
            for (Subscription sub : mSubscriptions.values()) {
                if (sub.conn == sender) continue;
                if (!sub.matchesKind(kind)) continue;
                if (!sub.matchesDTag(dTagValue)) continue;

                JSONArray eventMsg = new JSONArray();
                eventMsg.put("EVENT");
                eventMsg.put(sub.subscriptionId);
                eventMsg.put(event);

                Log.d(TAG, "Forwarding event " + eventId + " to subscription " + sub.subscriptionId);
                sub.conn.send(eventMsg.toString());
            }
        }
    }

    private void handleCloseSubscription(JSONArray msg) throws JSONException {
        String subscriptionId = msg.getString(1);
        synchronized (mSubscriptions) {
            mSubscriptions.remove(subscriptionId);
        }
        Log.d(TAG, "CLOSE subscription: " + subscriptionId);
    }

    private static class Subscription {
        final WebSocket conn;
        final String subscriptionId;
        final List<Integer> kinds;
        final List<String> dTagValues;

        Subscription(WebSocket conn, String subscriptionId, List<Integer> kinds, List<String> dTagValues) {
            this.conn = conn;
            this.subscriptionId = subscriptionId;
            this.kinds = kinds;
            this.dTagValues = dTagValues;
        }

        boolean matchesKind(int kind) {
            return kinds.isEmpty() || kinds.contains(kind);
        }

        boolean matchesDTag(String dTag) {
            return dTagValues.isEmpty() || (dTag != null && dTagValues.contains(dTag));
        }
    }

    private enum State {
        NOT_INITIALIZED, STARTED, STOPPED
    }
}
