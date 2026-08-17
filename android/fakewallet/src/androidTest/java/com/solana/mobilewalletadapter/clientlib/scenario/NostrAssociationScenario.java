package com.solana.mobilewalletadapter.clientlib.scenario;

import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.clientlib.transport.websockets.MobileWalletAdapterNostrWebSocket;
import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;
import com.solana.mobilewalletadapter.walletlib.transport.nostr.NostrCrypto;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NostrAssociationScenario extends Scenario {
    private static final String TAG = NostrAssociationScenario.class.getSimpleName();
    private static final int CONNECT_MAX_ATTEMPTS = 34;
    private static final int[] CONNECT_BACKOFF_SCHEDULE_MS = { 150, 150, 200, 500, 500, 750, 750, 1000 }; // == 30s, which allows time for a user to choose a wallet from the disambiguation dialog, and for that wallet to start
    private static final int CONNECT_TIMEOUT_MS = 30000;

    @NonNull
    private final URI mWebSocketUri;
    @NonNull
    private final byte[] mNostrPrivateKey;
    @NonNull
    private final String mNostrPubkey;
    @NonNull
    private final String mSessionIdentifier;

    // All access to these members must be protected by mLock
    private final Object mLock = new Object();
    private State mState = State.NOT_STARTED;
    private int mConnectionAttempts = 0;
    private MobileWalletAdapterSession mMobileWalletAdapterSession; // valid in all states except State.CLOSED
    private MobileWalletAdapterNostrWebSocket mMobileWalletAdapterWebSocket;
    private ScheduledExecutorService mConnectionBackoffExecutor; // valid in State.CONNECTING
    private NotifyingCompletableFuture<MobileWalletAdapterClient> mSessionEstablishedFuture; // valid in State.CONNECTING and State.ESTABLISHING_SESSION
    private ArrayList<NotifyingCompletableFuture<Void>> mClosedFuture; // _may_ be valid in State.CLOSING

    public interface ReadyCallback {
        void onReady(NostrAssociationScenario scenario);
    }
    private final ReadyCallback mReadyCallback;

    public String getNostrPubkey() {
        return mNostrPubkey;
    }

    public String getSessionIdentifier() {
        return mSessionIdentifier;
    }

    public MobileWalletAdapterSession getSession() {
        return mMobileWalletAdapterSession;
    }

    public NostrAssociationScenario(@NonNull String relayDomain,
                                    @IntRange(from = 0) int clientTimeoutMs,
                                    @NonNull ReadyCallback readyCallback) {
        this(WebSocketsTransportContract.WEBSOCKETS_REFLECTOR_SCHEME, relayDomain, clientTimeoutMs, readyCallback);
    }

    // Only for testing
    public NostrAssociationScenario(@NonNull String scheme, @NonNull String relayDomain,
                                    @IntRange(from = 0) int clientTimeoutMs,
                                    @NonNull ReadyCallback readyCallback) {
        super(clientTimeoutMs);

        try {
            mWebSocketUri = new URI(scheme, relayDomain, null, null, null);
        } catch (URISyntaxException e) {
            throw new UnsupportedOperationException("Failed assembling a WebSocket URI", e);
        }

        mNostrPrivateKey = NostrCrypto.generatePrivateKey();
        mNostrPubkey = NostrCrypto.bytesToHex(NostrCrypto.getXOnlyPublicKey(mNostrPrivateKey));

        mMobileWalletAdapterSession = new MobileWalletAdapterSession(
                mMobileWalletAdapterClient,
                mSessionStateCallbacks);

        mSessionIdentifier = NostrCrypto.deriveSessionIdentifier(
                mMobileWalletAdapterSession.getEncodedAssociationPublicKey());

        mReadyCallback = readyCallback;

        Log.v(TAG, "Creating Nostr association scenario for " + mWebSocketUri);
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
        mMobileWalletAdapterWebSocket = new MobileWalletAdapterNostrWebSocket(mWebSocketUri,
                mSessionIdentifier, mNostrPrivateKey,
                mMobileWalletAdapterSession, mWebSocketStateCallbacks, CONNECT_TIMEOUT_MS);
        mMobileWalletAdapterWebSocket.connect(); // [async]
    }

    @GuardedBy("mLock")
    private void doConnected() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.v(TAG, "WebSocket connection established, waiting for wallet CONNECT");
        mState = State.CONNECTED;
        mConnectionBackoffExecutor.shutdownNow();
        mConnectionBackoffExecutor = null;
        mReadyCallback.onReady(this);
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
        assert(mState == State.CONNECTING || mState == State.CONNECTED || mState == State.ESTABLISHING_SESSION || mState == State.STARTED || mState == State.CLOSING);
        if (mState == State.CONNECTING || mState == State.CONNECTED || mState == State.ESTABLISHING_SESSION) {
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
    private void doReflectionEstablished() {
        assert(mState == State.CONNECTED || mState == State.CLOSING);
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

    private final MobileWalletAdapterNostrWebSocket.StateCallbacks mWebSocketStateCallbacks = new MobileWalletAdapterNostrWebSocket.StateCallbacks() {
        @Override
        public void onConnected() {}

        @Override
        public void onSubscribed() {
            synchronized (mLock) {
                doConnected();
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
        NOT_STARTED, CONNECTING, CONNECTED,
        ESTABLISHING_SESSION, STARTED, CLOSING, CLOSED
    }

    public static class ConnectionFailedException extends RuntimeException {
        public ConnectionFailedException(@NonNull String message) { super(message); }
    }
}
