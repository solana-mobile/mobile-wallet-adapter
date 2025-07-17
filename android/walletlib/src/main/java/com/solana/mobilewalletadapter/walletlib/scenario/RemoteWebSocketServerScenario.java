/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.common.protocol.SessionProperties;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.walletlib.transport.websockets.ReflectorWebSocket;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RemoteWebSocketServerScenario extends BaseScenario {
    private static final String TAG = RemoteWebSocketServerScenario.class.getSimpleName();
    private static final int CONNECT_MAX_ATTEMPTS = 34;
    private static final int[] CONNECT_BACKOFF_SCHEDULE_MS = { 150, 150, 200, 500, 500, 750, 750, 1000 }; // == 30s, which allows time for a user to choose a wallet from the disambiguation dialog, and for that wallet to start
    private static final int CONNECT_TIMEOUT_MS = 30000;

    public final byte[] reflectorIdBytes;
    @NonNull
    private final URI mWebSocketUri;

    @Deprecated(forRemoval = true)
    @WebSocketsTransportContract.ReflectorIdRange
    public final long reflectorId = 0;

    // All access to these members must be protected by mLock
    private State mState = State.NOT_STARTED;
    private int mConnectionAttempts = 0;
    private ReflectorWebSocket mReflectorWebSocket;
    private ScheduledExecutorService mConnectionBackoffExecutor; // valid in State.CONNECTING

    @Deprecated(forRemoval = true)
    public RemoteWebSocketServerScenario(@NonNull Context context,
                                         @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                         @NonNull AuthIssuerConfig authIssuerConfig,
                                         @NonNull Callbacks callbacks,
                                         @NonNull byte[] associationPublicKey,
                                         @NonNull String hostAuthority,
                                         @WebSocketsTransportContract.ReflectorIdRange long reflectorId) {
        super(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey, List.of());
        throw new UnsupportedOperationException("numeric reflector ID's are no longer supported, reflector ID must be a byte array");
    }

    @Deprecated(forRemoval = true)
    public RemoteWebSocketServerScenario(@NonNull Context context,
                                         @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                         @NonNull AuthIssuerConfig authIssuerConfig,
                                         @NonNull Callbacks callbacks,
                                         @NonNull byte[] associationPublicKey,
                                         @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                                         @NonNull String hostAuthority,
                                         @WebSocketsTransportContract.ReflectorIdRange long reflectorId) {
        super(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey, associationProtocolVersions);
        throw new UnsupportedOperationException("numeric reflector ID's are no longer supported, reflector ID must be a byte array");
    }

    @Deprecated(forRemoval = true)
    public RemoteWebSocketServerScenario(@NonNull Context context,
                                         @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                         @NonNull AuthIssuerConfig authIssuerConfig,
                                         @NonNull Callbacks callbacks,
                                         @NonNull byte[] associationPublicKey,
                                         @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                                         @NonNull String scheme,
                                         @NonNull String hostAuthority,
                                         @WebSocketsTransportContract.ReflectorIdRange long reflectorId) {
        super(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey, associationProtocolVersions);
        throw new UnsupportedOperationException("numeric reflector ID's are no longer supported, reflector ID must be a byte array");
    }

    public RemoteWebSocketServerScenario(@NonNull Context context,
                                         @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                         @NonNull AuthIssuerConfig authIssuerConfig,
                                         @NonNull Callbacks callbacks,
                                         @NonNull byte[] associationPublicKey,
                                         @NonNull String hostAuthority,
                                         @NonNull byte[] reflectorId) {
        this(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey,
                List.of(), hostAuthority, reflectorId);
    }

    public RemoteWebSocketServerScenario(@NonNull Context context,
                                         @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                         @NonNull AuthIssuerConfig authIssuerConfig,
                                         @NonNull Callbacks callbacks,
                                         @NonNull byte[] associationPublicKey,
                                         @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                                         @NonNull String hostAuthority,
                                         @NonNull byte[] reflectorId) {
        this(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey,
                WebSocketsTransportContract.WEBSOCKETS_REFLECTOR_SCHEME, hostAuthority, reflectorId, associationProtocolVersions, new DefaultWalletIconProvider(context));
    }

    public RemoteWebSocketServerScenario(@NonNull Context context,
                                         @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                         @NonNull AuthIssuerConfig authIssuerConfig,
                                         @NonNull Callbacks callbacks,
                                         @NonNull byte[] associationPublicKey,
                                         @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                                         @NonNull String scheme,
                                         @NonNull String hostAuthority,
                                         @NonNull byte[] reflectorId) {
        this(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey,
                scheme, hostAuthority, reflectorId, associationProtocolVersions, new DefaultWalletIconProvider(context));

    }

    /*package*/ RemoteWebSocketServerScenario(@NonNull Context context,
                                              @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                              @NonNull AuthIssuerConfig authIssuerConfig,
                                              @NonNull Callbacks callbacks,
                                              @NonNull byte[] associationPublicKey,
                                              @NonNull String scheme,
                                              @NonNull String hostAuthority,
                                              @NonNull byte[] reflectorId,
                                              @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                                              @NonNull WalletIconProvider iconProvider) {
        super(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey,
                associationProtocolVersions, iconProvider);
        this.reflectorIdBytes = reflectorId;
        String reflectorIdUrl = Base64.encodeToString(reflectorId,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

        try {
            mWebSocketUri = new URI(scheme, hostAuthority, WebSocketsTransportContract.WEBSOCKETS_REFLECTOR_PATH,
                    WebSocketsTransportContract.WEBSOCKETS_REFLECTOR_ID_QUERY + "=" + reflectorIdUrl, null);
        } catch (URISyntaxException e) {
            throw new UnsupportedOperationException("Failed assembling a WebSocket URI", e);
        }
    }

    public NotifyingCompletableFuture<String> startAsync() {
        final NotifyingCompletableFuture<String> future;

        synchronized (mLock) {
            if (mState != State.NOT_STARTED) {
                throw new IllegalStateException("Scenario has already been started");
            }

            mState = State.CONNECTING;
            future = super.startAsync();
            doTryConnect();

            mConnectionBackoffExecutor = Executors.newScheduledThreadPool(1);
        }

        return future;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            switch (mState) {
                case NOT_STARTED:
                    // If not started, just teardown immediately
                    mState = State.CLOSED;
                    destroyResourcesOnClose();
                    break;
                case CONNECTING:
                    mState = State.CLOSING;
                    notifySessionEstablishmentFailed("Scenario closed while connecting");

                    if (mReflectorWebSocket != null) {
                        mReflectorWebSocket.close();
                    } else {
                        // In the middle of a backoff - we can tear down immediately
                        mState = State.CLOSED;
                        destroyResourcesOnClose();
                    }
                    break;
                case AWAITING_REFLECTION: 
                case ESTABLISHING_SESSION:
                    mState = State.CLOSING;
                    notifySessionEstablishmentFailed("Scenario closed during session establishment");
                    mReflectorWebSocket.close();
                    break;
                case STARTED:
                    mState = State.CLOSING;
                    mReflectorWebSocket.close();
                    break;
                case CLOSING:
                    // Already closing - nothing to do here
                    break;
                case CLOSED:
                    // No-op; scenario is either already complete, or in the process of being torn down
                    break;
                default:
                    throw new IllegalStateException("Error: attempt to close in an unknown state");
            }
        }
    }

    @Override
    public MessageReceiver createMessageReceiver() {
        return new MobileWalletAdapterSession(
                this,
                new MobileWalletAdapterServer(mMobileWalletAdapterConfig, mIoLooper, mMethodHandlers),
                mSessionStateCallbacks);
    }

    @GuardedBy("mLock")
    private void doTryConnect() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        mReflectorWebSocket = new ReflectorWebSocket(mWebSocketUri,
                createMessageReceiver(), mWebSocketStateCallbacks, CONNECT_TIMEOUT_MS);
        mReflectorWebSocket.connect(); // [async]
    }

    @GuardedBy("mLock")
    private void doConnected() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.v(TAG, "WebSocket connection established, waiting for session establishment");
        mState = State.AWAITING_REFLECTION;
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
            mReflectorWebSocket = null;
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
    private void doReflectionEstablished() {
        assert(mState == State.AWAITING_REFLECTION || mState == State.CLOSING);
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
        mCallbacks.onScenarioReady();
    }

    @GuardedBy("mLock")
    private void doDisconnected() {
        assert(mState == State.CONNECTING || mState == State.AWAITING_REFLECTION ||
                mState == State.ESTABLISHING_SESSION || mState == State.STARTED || mState == State.CLOSING);
        if (mState == State.CONNECTING || mState == State.AWAITING_REFLECTION || mState == State.ESTABLISHING_SESSION) {
            String message = mState == State.AWAITING_REFLECTION ?
                    "Disconnected before reflection established, check your reflector ID" :
                    "Disconnected before session established";
            Log.w(TAG, message);
            mState = State.CLOSING;
            notifySessionEstablishmentFailed(message);
        } else {
            Log.d(TAG, "Disconnected during normal operation");
        }
        mState = State.CLOSED;
        mCallbacks.onScenarioComplete();
        destroyResourcesOnClose();
        mCallbacks.onScenarioTeardownComplete();
    }

    @GuardedBy("mLock")
    private void destroyResourcesOnClose() {
        mReflectorWebSocket = null;
        if (mConnectionBackoffExecutor != null) {
            mConnectionBackoffExecutor.shutdownNow();
            mConnectionBackoffExecutor = null;
        }
    }

    @NonNull
    private final ReflectorWebSocket.StateCallbacks mWebSocketStateCallbacks = new ReflectorWebSocket.StateCallbacks() {
        @Override
        public void onConnected() {
            synchronized (mLock) {
                doConnected();
            }
        }

        @Override
        public void onConnectionFailed() {
            synchronized (mLock) {
                doConnectionFailed();
            }
        }

        @Override
        public void onReflectionEstablished() {
            synchronized (mLock) {
                doReflectionEstablished();
            }
        }

        @Override
        public void onConnectionClosed() {
            synchronized (mLock) {
                doDisconnected();
            }
        }
    };

    private final MobileWalletAdapterSessionCommon.StateCallbacks mSessionStateCallbacks =
            new MobileWalletAdapterSessionCommon.StateCallbacks() {
                private final AtomicInteger mClientCount = new AtomicInteger();

                @Override
                public void onSessionEstablished() {
                    Log.d(TAG, "MobileWalletAdapter session established");
                    synchronized (mLock) {
                        doSessionEstablished();
                    }
                    if (mClientCount.incrementAndGet() == 1) {
                        mIoHandler.post(mAuthRepository::start);
                        mIoHandler.post(mCallbacks::onScenarioServingClients);
                    }
                }

                @Override
                public void onSessionClosed() {
                    Log.d(TAG, "MobileWalletAdapter session terminated");
                    if (mClientCount.decrementAndGet() == 0) {
                        synchronized (mLock) {
                            mActiveAuthorization = null;
                        }
                        mIoHandler.post(mCallbacks::onScenarioServingComplete);
                        mIoHandler.post(mAuthRepository::stop);
                    }
                }

                @Override
                public void onSessionError() {
                    Log.w(TAG, "MobileWalletAdapter session error");
                    if (mClientCount.decrementAndGet() == 0) {
                        synchronized (mLock) {
                            mActiveAuthorization = null;
                        }
                        mIoHandler.post(mCallbacks::onScenarioServingComplete);
                        mIoHandler.post(mAuthRepository::stop);
                    }
                }
            };

    private enum State {
        NOT_STARTED, CONNECTING, AWAITING_REFLECTION, ESTABLISHING_SESSION, STARTED, CLOSING, CLOSED
    }
}
