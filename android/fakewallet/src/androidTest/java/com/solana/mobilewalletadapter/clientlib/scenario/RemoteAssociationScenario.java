/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.scenario;

import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.clientlib.transport.websockets.MobileWalletAdapterRemoteWebSocket;
import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RemoteAssociationScenario extends Scenario {
    private static final String TAG = RemoteAssociationScenario.class.getSimpleName();
    private static final int CONNECT_MAX_ATTEMPTS = 34;
    private static final int[] CONNECT_BACKOFF_SCHEDULE_MS = { 150, 150, 200, 500, 500, 750, 750, 1000 }; // == 30s, which allows time for a user to choose a wallet from the disambiguation dialog, and for that wallet to start
    private static final int CONNECT_TIMEOUT_MS = 30000;

    @NonNull
    private final String mHostAuthority;
    @NonNull
    private final URI mWebSocketUri;

    // All access to these members must be protected by mLock
    private final Object mLock = new Object();
    private State mState = State.NOT_STARTED;
    private int mConnectionAttempts = 0;
    private MobileWalletAdapterSession mMobileWalletAdapterSession; // valid in all states except State.CLOSED
    private MobileWalletAdapterRemoteWebSocket mMobileWalletAdapterWebSocket;
    private ScheduledExecutorService mConnectionBackoffExecutor; // valid in State.CONNECTING
    private NotifyingCompletableFuture<MobileWalletAdapterClient> mSessionEstablishedFuture; // valid in State.CONNECTING and State.ESTABLISHING_SESSION
    private ArrayList<NotifyingCompletableFuture<Void>> mClosedFuture; // _may_ be valid in State.CLOSING

    public interface ReflectorIdCallback {
        void reflectorIdReceived(RemoteAssociationScenario scenario, byte[] reflectorId);
    }
    private ReflectorIdCallback mReflectorIdCallback;

    public String getHostAuthority() {
        return mHostAuthority;
    }

    public MobileWalletAdapterSession getSession() {
        return mMobileWalletAdapterSession;
    }

    public RemoteAssociationScenario(@NonNull String hostAuthority,
                                     @IntRange(from = 0) int clientTimeoutMs,
                                     @NonNull ReflectorIdCallback reflectorIdCallback) {
        this(WebSocketsTransportContract.WEBSOCKETS_REFLECTOR_SCHEME, hostAuthority, clientTimeoutMs, reflectorIdCallback);
    }

    // Only for testing
    public RemoteAssociationScenario(@NonNull String scheme, @NonNull String hostAuthority,
                                     @IntRange(from = 0) int clientTimeoutMs,
                                     @NonNull ReflectorIdCallback reflectorIdCallback) {
        super(clientTimeoutMs);

        mHostAuthority = hostAuthority;
        try {
            mWebSocketUri = new URI(scheme, hostAuthority,
                    WebSocketsTransportContract.WEBSOCKETS_REFLECTOR_PATH, null, null);
        } catch (URISyntaxException e) {
            throw new UnsupportedOperationException("Failed assembling a WebSocket URI", e);
        }

        mMobileWalletAdapterSession = new MobileWalletAdapterSession(
                mMobileWalletAdapterClient,
                mSessionStateCallbacks);

        mReflectorIdCallback = reflectorIdCallback;

        Log.v(TAG, "Creating remote association scenario for " + mWebSocketUri);
    }

    @Override
    public NotifyOnCompleteFuture<MobileWalletAdapterClient> start() {
        final NotifyingCompletableFuture<MobileWalletAdapterClient> future;

        synchronized (mLock) {
            if (mState != State.NOT_STARTED) {
                throw new IllegalStateException("Scenario has already been started");
            }

            mState = State.CONNECTING;
            future = startDeferredFuture();

            // Delay the first connect to allow the association intent receiver to start the WebSocket
            // server
            mConnectionBackoffExecutor = Executors.newScheduledThreadPool(1);
            mConnectionBackoffExecutor.schedule(this::doTryConnect, CONNECT_BACKOFF_SCHEDULE_MS[0], TimeUnit.MILLISECONDS);
        }

        return future;
    }

    @Override
    public NotifyOnCompleteFuture<Void> close() {
        final NotifyingCompletableFuture<Void> future;

        synchronized (mLock) {
            switch (mState) {
                case NOT_STARTED:
                    // If not started, just teardown immediately
                    mState = State.CLOSED;
                    destroyResourcesOnClose();
                    future = closedImmediatelyFuture();
                    break;
                case CONNECTING:
                    mState = State.CLOSING;
                    notifySessionEstablishmentFailed("Scenario closed while connecting");

                    if (mMobileWalletAdapterWebSocket != null) {
                        future = closeDeferredFuture();
                        mMobileWalletAdapterWebSocket.close();
                    } else {
                        // In the middle of a backoff - we can tear down immediately
                        mState = State.CLOSED;
                        destroyResourcesOnClose();
                        future = closedImmediatelyFuture();
                    }
                    break;
                case ESTABLISHING_SESSION:
                    mState = State.CLOSING;
                    notifySessionEstablishmentFailed("Scenario closed during session establishment");
                    future = closeDeferredFuture();
                    mMobileWalletAdapterWebSocket.close();
                    break;
                case STARTED:
                    mState = State.CLOSING;
                    future = closeDeferredFuture();
                    mMobileWalletAdapterWebSocket.close();
                    break;
                case CLOSING:
                    // Already closing - nothing to do here
                    future = closeDeferredFuture();
                    break;
                case CLOSED:
                    // No-op; scenario is either already complete, or in the process of being torn down
                    future = closedImmediatelyFuture();
                    break;
                default:
                    throw new IllegalStateException("Error: attempt to close in an unknown state");
            }
        }

        return future;
    }

    @NonNull
    @GuardedBy("mLock")
    private NotifyingCompletableFuture<MobileWalletAdapterClient> startDeferredFuture() {
        assert(mState == State.CONNECTING && mSessionEstablishedFuture == null);
        final NotifyingCompletableFuture<MobileWalletAdapterClient> future = new NotifyingCompletableFuture<>();
        mSessionEstablishedFuture = future;
        return future;
    }

    @NonNull
    @GuardedBy("mLock")
    private NotifyingCompletableFuture<Void> closedImmediatelyFuture() {
        assert(mState == State.CLOSED);
        final NotifyingCompletableFuture<Void> future = new NotifyingCompletableFuture<>();
        future.complete(null);
        return future;
    }

    @NonNull
    @GuardedBy("mLock")
    private NotifyingCompletableFuture<Void> closeDeferredFuture() {
        assert(mState == State.CLOSING);
        final NotifyingCompletableFuture<Void> future = new NotifyingCompletableFuture<>();
        if (mClosedFuture == null) {
            mClosedFuture = new ArrayList<>(1);
        }
        mClosedFuture.add(future);
        return future;
    }

    @GuardedBy("mLock")
    private void doTryConnect() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        mMobileWalletAdapterWebSocket = new MobileWalletAdapterRemoteWebSocket(mWebSocketUri,
                mMobileWalletAdapterSession, mWebSocketStateCallbacks, CONNECT_TIMEOUT_MS);
        mMobileWalletAdapterWebSocket.connect(); // [async]
    }

    @GuardedBy("mLock")
    private void doConnected() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.v(TAG, "WebSocket connection established, waiting for reflector ID");
        mState = State.AWAITING_REFLECTOR_ID;
        mConnectionBackoffExecutor.shutdownNow();
        mConnectionBackoffExecutor = null;
    }

    @GuardedBy("mLock")
    private void doConnectionFailed() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        if (++mConnectionAttempts < CONNECT_MAX_ATTEMPTS) {
            final int delay = CONNECT_BACKOFF_SCHEDULE_MS[
                    mConnectionAttempts < CONNECT_BACKOFF_SCHEDULE_MS.length ?
                            mConnectionAttempts :
                            CONNECT_BACKOFF_SCHEDULE_MS.length - 1];
            Log.d(TAG, "Connect attempt failed, retrying in " + delay + " ms");
            mMobileWalletAdapterWebSocket = null;
            mConnectionBackoffExecutor.schedule(this::doTryConnect, delay, TimeUnit.MILLISECONDS);
        } else {
            Log.w(TAG, "Failed establishing a WebSocket connection");

            // We never connected, so we won't get an onConnectionClosed; do cleanup directly here
            mState = State.CLOSED;
            destroyResourcesOnClose();
            notifySessionEstablishmentFailed("Unable to connect to websocket server");
        }
    }

    @GuardedBy("mLock")
    private void doDisconnected() {
        assert(mState == State.CONNECTING || mState == State.ESTABLISHING_SESSION || mState == State.STARTED || mState == State.CLOSING);
        if (mState == State.CONNECTING || mState == State.ESTABLISHING_SESSION) {
            Log.w(TAG, "Disconnected before session established");
            mState = State.CLOSING;
            notifySessionEstablishmentFailed("Disconnected before session established");
        } else {
            Log.d(TAG, "Disconnected during normal operation");
        }
        mState = State.CLOSED;
        destroyResourcesOnClose();
        notifyCloseCompleted();
    }

    @GuardedBy("mLock")
    private void doReflectorIdReceived(byte[] reflectorId) {
        assert(mState == State.AWAITING_REFLECTOR_ID || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.v(TAG, "WebSocket reflector Id received, waiting for reflection");
        mState = State.REFLECTOR_ID_RECEIVED;
        if (mReflectorIdCallback != null) {
            mReflectorIdCallback.reflectorIdReceived(this, reflectorId);
        }
    }

    @GuardedBy("mLock")
    private void doReflectionEstablished() {
        assert(mState == State.REFLECTOR_ID_RECEIVED || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.v(TAG, "WebSocket reflection established, waiting for session establishment");
        mState = State.ESTABLISHING_SESSION;
    }

    @GuardedBy("mLock")
    private void doSessionEstablished() {
        assert(mState == State.ESTABLISHING_SESSION || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.d(TAG, "Session established, scenario ready for use");
        mState = State.STARTED;
        notifySessionEstablishmentSucceeded();
    }

    @GuardedBy("mLock")
    private void doSessionClosed() {
        assert(mState == State.ESTABLISHING_SESSION || mState == State.STARTED || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        if (mState == State.ESTABLISHING_SESSION) {
            Log.d(TAG, "Session terminated before session establishment");
            mState = State.CLOSING;
            notifySessionEstablishmentFailed("Session terminated before session establishment");
        } else {
            Log.d(TAG, "Session terminated normally");
            mState = State.CLOSING;
        }
        // doDisconnected is responsible for connection cleanup
    }

    @GuardedBy("mLock")
    private void doSessionError() {
        assert(mState == State.ESTABLISHING_SESSION || mState == State.STARTED || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        if (mState == State.ESTABLISHING_SESSION) {
            Log.w(TAG, "Session error, terminating before session established");
            mState = State.CLOSING;
            notifySessionEstablishmentFailed("Closing before session establishment due to session error");
        } else {
            Log.w(TAG, "Session error, terminating");
            mState = State.CLOSING;
        }
        // doDisconnected is responsible for connection cleanup
    }

    @GuardedBy("mLock")
    private void destroyResourcesOnClose() {
        mMobileWalletAdapterSession = null;
        mMobileWalletAdapterWebSocket = null;
        if (mConnectionBackoffExecutor != null) {
            mConnectionBackoffExecutor.shutdownNow();
            mConnectionBackoffExecutor = null;
        }
    }

    @GuardedBy("mLock")
    private void notifySessionEstablishmentSucceeded() {
        mSessionEstablishedFuture.complete(mMobileWalletAdapterClient);
        mSessionEstablishedFuture = null;
    }

    @GuardedBy("mLock")
    private void notifySessionEstablishmentFailed(@NonNull String message) {
        mSessionEstablishedFuture.completeExceptionally(new ConnectionFailedException(message));
        mSessionEstablishedFuture = null;
    }

    @GuardedBy("mLock")
    private void notifyCloseCompleted() {
        // NOTE: if close was initiated by counterparty, there won't be a closed future to complete
        if (mClosedFuture != null) {
            for (NotifyingCompletableFuture<Void> future : mClosedFuture) {
                future.complete(null);
            }
            mClosedFuture = null;
        }
    }

    private final MobileWalletAdapterRemoteWebSocket.StateCallbacks mWebSocketStateCallbacks = new MobileWalletAdapterRemoteWebSocket.StateCallbacks() {
        @Override
        public void onConnected() {
            synchronized (mLock) {
                doConnected();
            }
        }

        @Override
        public void onReflectorIdReceived(byte[] reflectorId) {
            synchronized (mLock) {
                doReflectorIdReceived(reflectorId);
            }
        }

        @Override
        public void onReflectionEstablished() {
            synchronized (mLock) {
                doReflectionEstablished();
            }
        }

        @Override
        public void onConnectionFailed() {
            synchronized (mLock) {
                doConnectionFailed();
            }
        }

        @Override
        public void onConnectionClosed() {
            synchronized (mLock) {
                doDisconnected();
            }
        }
    };

    private final MobileWalletAdapterSessionCommon.StateCallbacks mSessionStateCallbacks = new MobileWalletAdapterSessionCommon.StateCallbacks() {
        @Override
        public void onSessionEstablished() {
            synchronized (mLock) {
                doSessionEstablished();
            }
        }

        @Override
        public void onSessionError() {
            synchronized (mLock) {
                doSessionError();
            }
        }

        @Override
        public void onSessionClosed() {
            synchronized (mLock) {
                doSessionClosed();
            }
        }
    };

    private enum State {
        NOT_STARTED, CONNECTING, AWAITING_REFLECTOR_ID, REFLECTOR_ID_RECEIVED,
        ESTABLISHING_SESSION, STARTED, CLOSING, CLOSED
    }

    public static class ConnectionFailedException extends RuntimeException {
        public ConnectionFailedException(@NonNull String message) { super(message); }
    }
}
