/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.transport.websockets.server;

import android.util.Log;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;
import com.solana.mobilewalletadapter.walletlib.scenario.LocalWebSocketServerScenario;

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
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class LocalWebSocketServer extends WebSocketServer {
    private static final String TAG = LocalWebSocketServer.class.getSimpleName();
    private static final int PING_TIME_SEC = 45; // send a ping every 45s, disconnect if no pong received for 1.5x 45s == 67.5s
    private static final int CLOSE_TIME_MS = 5000; // allow 5s for connections to close cleanly, then terminate them

    @NonNull
    private final LocalWebSocketServerScenario mScenario;
    @NonNull
    private final Callbacks mCallbacks;
    @NonNull
    private State mState = State.NOT_INITIALIZED;

    public LocalWebSocketServer(@NonNull LocalWebSocketServerScenario scenario,
                                @NonNull Callbacks callbacks) {
        // Create a WebSocket server on localhost:${scenario.port}, with 1 decoding thread, which
        // only accepts connections for protocol WebSocketsTransportContract.WEBSOCKETS_PROTOCOL
        super(new InetSocketAddress(WebSocketsTransportContract.WEBSOCKETS_LOCAL_HOST, scenario.port), 1,
                Collections.singletonList(new Draft_6455(Collections.emptyList(), Collections.singletonList(
                        new Protocol(WebSocketsTransportContract.WEBSOCKETS_PROTOCOL)))));
        setConnectionLostTimeout(PING_TIME_SEC);
        setWebSocketFactory(new MobileWalletAdapterWebSocketServerFactory());
        mScenario = scenario;
        mCallbacks = callbacks;
    }

    public void init() {
        if (mState == State.NOT_INITIALIZED) {
            Log.i(TAG, "Starting local mobile-wallet-adapter WebSocket server on port " + mScenario.port);
            mState = State.STARTED;
            start();
        } else {
            Log.w(TAG, "Cannot start local mobile-wallet-adapter WebSocket server in " + mState);
        }
    }

    public void close() {
        if (mState == State.STARTED) {
            Log.i(TAG, "Stopping local mobile-wallet-adapter WebSocket server");
            try {
                stop(CLOSE_TIME_MS, "WS server shutting down");
            } catch (InterruptedException ignored) {}
        }
        mState = State.STOPPED;
    }

    @Override
    public void onStart() {
        mCallbacks.onStarted();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.d(TAG, "mobile-wallet-adapter WebSocket opened");
        final MobileWalletAdapterWebSocket ws = (MobileWalletAdapterWebSocket) conn;
        final MessageReceiver mr = mScenario.createMessageReceiver();
        ws.messageReceiver = mr;
        mr.receiverConnected(ws);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "mobile-wallet-adapter WebSocket closed");
        final MobileWalletAdapterWebSocket ws = (MobileWalletAdapterWebSocket) conn;
        ws.messageReceiver.receiverDisconnected();
        ws.messageReceiver = null;
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "mobile-wallet-adapter WebSocket recv (text)");
        final MobileWalletAdapterWebSocket ws = (MobileWalletAdapterWebSocket) conn;
        ws.messageReceiver.receiverMessageReceived(message.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        Log.d(TAG, "mobile-wallet-adapter WebSocket recv (binary)");
        final byte[] bytes = new byte[message.remaining()];
        message.get(bytes);
        final MobileWalletAdapterWebSocket ws = (MobileWalletAdapterWebSocket) conn;
        ws.messageReceiver.receiverMessageReceived(bytes);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn == null) {
            Log.e(TAG, "mobile-wallet-adapter WebSocket FATAL exception", ex);
            mCallbacks.onFatalError();
        } else {
            Log.w(TAG, "mobile-wallet-adapter WebSocket exception", ex);
        }
    }

    private static class MobileWalletAdapterWebSocketServerFactory implements WebSocketServerFactory {
        @Override
        public WebSocketImpl createWebSocket(WebSocketAdapter a, Draft d) {
            return new MobileWalletAdapterWebSocket(a, d);
        }

        @Override
        public WebSocketImpl createWebSocket(WebSocketAdapter a, List<Draft> d) {
            return new MobileWalletAdapterWebSocket(a, d);
        }

        @Override
        public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) {
            return channel;
        }

        @Override
        public void close() {
        }
    }

    private static class MobileWalletAdapterWebSocket extends WebSocketImpl implements MessageSender {
        private MessageReceiver messageReceiver; // valid only after opened

        public MobileWalletAdapterWebSocket(WebSocketAdapter a, Draft d) {
            super(a, d);
        }

        public MobileWalletAdapterWebSocket(WebSocketAdapter a, List<Draft> d) {
            super(a, d);
        }

        // N.B. synchronize send() with WebSocketImpl.close()
        @Override
        public synchronized void send(@NonNull byte[] bytes) {
            Log.d(TAG, "mobile-wallet-adapter WebSocket send");
            super.send(bytes);
        }
    }

    private enum State {
        NOT_INITIALIZED, STARTED, STOPPED
    }

    public interface Callbacks {
        void onStarted();
        void onFatalError();
    }
}