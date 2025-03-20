/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.transport.websockets.server;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;
import com.solana.util.Varint;

import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketAdapter;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.WebSocketServerFactory;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSocketReflectorServer extends WebSocketServer {
    private static final String TAG = WebSocketReflectorServer.class.getSimpleName();
    private static final int PING_TIME_SEC = 45; // send a ping every 45s, disconnect if no pong received for 1.5x 45s == 67.5s
    private static final int CLOSE_TIME_MS = 5000; // allow 5s for connections to close cleanly, then terminate them

    @NonNull
    private State mState = State.NOT_INITIALIZED;

    @NonNull
    private final Map<String, ReflectorWebSocket> mHalfOpenConnections = new HashMap<>();
    @NonNull
    private final List<String> mFullOpenConnections = new ArrayList<>(1);

    private final Pattern idPattern = Pattern.compile("id=([A-Za-z0-9-_]*={0,3})");

    public WebSocketReflectorServer() { this(8080); }

    public WebSocketReflectorServer(int port) {
        // Create a WebSocket server on localhost:${port}, with 1 decoding thread, which
        // only accepts connections for protocol WebSocketsTransportContract.WEBSOCKETS_PROTOCOL
        super(new InetSocketAddress(WebSocketsTransportContract.WEBSOCKETS_LOCAL_HOST, port), 1,
                Collections.singletonList(new Draft_6455(Collections.emptyList(), Collections.singletonList(
                        new Protocol(WebSocketsTransportContract.WEBSOCKETS_PROTOCOL)))));
        setConnectionLostTimeout(PING_TIME_SEC);
        setWebSocketFactory(new ReflectorWebSocketServerFactory());
    }

    public void init() {
        if (mState == State.NOT_INITIALIZED) {
            Log.i(TAG, "Starting local reflector WebSocket server on port " + getPort());
            mState = State.STARTED;
            start();
        } else {
            Log.w(TAG, "Cannot start local reflector WebSocket server in " + mState);
        }
    }

    public void close() {
        if (mState == State.STARTED) {
            Log.i(TAG, "Stopping local reflector WebSocket server");
            try {
                stop(CLOSE_TIME_MS, "WS server shutting down");
            } catch (InterruptedException ignored) {}
        }
        mState = State.STOPPED;
    }

    @Override
    public void onStart() {
        Log.d(TAG, "reflector WebSocket started");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.d(TAG, "reflector WebSocket opened: " + handshake.getResourceDescriptor());
        final URI uri = URI.create(handshake.getResourceDescriptor());
        final ReflectorWebSocket ws = (ReflectorWebSocket) conn;
        final String query = uri.getQuery();
        String id;
        if (query == null) {
            // create id for new connection
            byte[] idBytes = new byte[32];
            new Random().nextBytes(idBytes);
            id = Base64.encodeToString(idBytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            ws.id = id;
            mHalfOpenConnections.put(id, ws);
            Log.d(TAG, "reflector WebSocket new half open connection: " + id);

            // send REFLECTOR_ID message to client
            byte[] reflectorIdLength = Varint.INSTANCE.encode(32);
            byte[] reflectorIdPayload = new byte[reflectorIdLength.length + idBytes.length];
            System.arraycopy(reflectorIdLength, 0, reflectorIdPayload, 0, reflectorIdLength.length);
            System.arraycopy(idBytes, 0, reflectorIdPayload, reflectorIdLength.length, idBytes.length);
            ws.send(reflectorIdPayload);
        } else {
            Matcher matcher = idPattern.matcher(query);
            if (matcher.find()) {
                id = matcher.group(1);
                ws.id = id;
                if (mFullOpenConnections.contains(id)) {
                    Log.d(TAG, "reflector WebSocket connection already exists for id: " + id + ", closing");
                    conn.close();
                } else if (mHalfOpenConnections.containsKey(id)) {
                    Log.d(TAG, "reflector WebSocket new fully open connection: " + id);
                    ReflectorWebSocket other = mHalfOpenConnections.get(id);
                    other.reflect = ws;
                    ws.reflect = other;
                    mHalfOpenConnections.remove(id);
                    mFullOpenConnections.add(id);

                    // Send APP_PING (empty message) to both clients
                    ws.send("");
                    other.send("");
                } else {
                    Log.d(TAG, "reflector WebSocket invalid id: " + id);
                    conn.close();
                }
            } else {
                Log.d(TAG, "reflector WebSocket invalid query: " + query);
                conn.close();
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "reflector WebSocket closed");
        final ReflectorWebSocket ws = (ReflectorWebSocket) conn;
        if (mHalfOpenConnections.containsKey(ws.id)) {
            Log.d(TAG, "reflector WebSocket closing half open connection " + ws.id);
            mHalfOpenConnections.remove(ws.id);
        } else if (mFullOpenConnections.contains(ws.id)) {
            Log.d(TAG, "reflector WebSocket closing fully open connection " + ws.id);
            mFullOpenConnections.remove(ws.id);
            ws.reflect.close();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "reflector WebSocket recv (text)");
        final ReflectorWebSocket ws = (ReflectorWebSocket) conn;
        if (ws.reflect != null) {
            Log.d(TAG, "reflector WebSocket reflecting on " + ws.id);
            ws.reflect.send(message);
        } else {
            Log.d(TAG, "reflector WebSocket received message on half open connection (" + ws.id + "), ignoring");
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        Log.d(TAG, "reflector WebSocket recv (binary)");
        final ReflectorWebSocket ws = (ReflectorWebSocket) conn;
        if (mFullOpenConnections.contains(ws.id)) {
            Log.d(TAG, "reflector WebSocket reflecting on " + ws.id);
            ws.reflect.send(message);
        } else {
            Log.d(TAG, "reflector WebSocket received message on half open connection (" + ws.id + "), ignoring");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn == null) {
            Log.e(TAG, "reflector WebSocket FATAL exception", ex);
            close();
        } else {
            Log.w(TAG, "reflector WebSocket exception", ex);
        }
    }

    private static class ReflectorWebSocketServerFactory implements WebSocketServerFactory {
        @Override
        public WebSocketImpl createWebSocket(WebSocketAdapter a, Draft d) {
            return new ReflectorWebSocket(a, d);
        }

        @Override
        public WebSocketImpl createWebSocket(WebSocketAdapter a, List<Draft> d) {
            return new ReflectorWebSocket(a, d);
        }

        @Override
        public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) {
            return channel;
        }

        @Override
        public void close() {
        }
    }

    private static class ReflectorWebSocket extends WebSocketImpl implements MessageSender {
        private String id; // valid only after opened
        private WebSocket reflect; // valid only after full connection established

        public ReflectorWebSocket(WebSocketAdapter a, Draft d) {
            super(a, d);
        }

        public ReflectorWebSocket(WebSocketAdapter a, List<Draft> d) {
            super(a, d);
        }

        // N.B. synchronize send() with WebSocketImpl.close()
        @Override
        public synchronized void send(@NonNull byte[] bytes) {
            Log.d(TAG, "reflector WebSocket send");
            super.send(bytes);
        }
    }

    private enum State {
        NOT_INITIALIZED, STARTED, STOPPED
    }
}
