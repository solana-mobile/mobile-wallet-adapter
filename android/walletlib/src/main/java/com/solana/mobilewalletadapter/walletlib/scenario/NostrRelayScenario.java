/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;
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
import com.solana.mobilewalletadapter.walletlib.transport.nostr.NostrCrypto;
import com.solana.mobilewalletadapter.walletlib.transport.nostr.NostrRelay;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NostrRelayScenario extends BaseScenario {
    private static final String TAG = NostrRelayScenario.class.getSimpleName();
    private static final int CONNECT_MAX_ATTEMPTS = 34;
    private static final int[] CONNECT_BACKOFF_SCHEDULE_MS = { 150, 150, 200, 500, 500, 750, 750, 1000 };
    private static final int CONNECT_TIMEOUT_MS = 30000;

    @NonNull
    private final String mScheme;
    @NonNull
    private final String mRelayDomain;
    @NonNull
    private final String mDappNostrPubkey;
    @NonNull
    private final byte[] mNostrPrivateKey;
    @NonNull
    private final String mSessionIdentifier;

    @GuardedBy("mLock")
    private State mState = State.NOT_STARTED;
    @GuardedBy("mLock")
    private int mConnectionAttempts = 0;
    @GuardedBy("mLock")
    private NostrRelay mNostrRelay;
    @GuardedBy("mLock")
    private ScheduledExecutorService mConnectionBackoffExecutor;

    public NostrRelayScenario(@NonNull Context context,
                              @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                              @NonNull AuthIssuerConfig authIssuerConfig,
                              @NonNull Callbacks callbacks,
                              @NonNull byte[] associationPublicKey,
                              @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                              @NonNull String relayDomain,
                              @NonNull String dappNostrPubkey) {
        this(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks,
                associationPublicKey, associationProtocolVersions,
                WebSocketsTransportContract.WEBSOCKETS_REFLECTOR_SCHEME, relayDomain, dappNostrPubkey);
    }

    public NostrRelayScenario(@NonNull Context context,
                              @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                              @NonNull AuthIssuerConfig authIssuerConfig,
                              @NonNull Callbacks callbacks,
                              @NonNull byte[] associationPublicKey,
                              @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                              @NonNull String scheme,
                              @NonNull String relayDomain,
                              @NonNull String dappNostrPubkey) {
        this(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks,
                associationPublicKey, associationProtocolVersions,
                scheme, relayDomain, dappNostrPubkey, new DefaultWalletIconProvider(context));
    }

    /*package*/ NostrRelayScenario(@NonNull Context context,
                                   @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                                   @NonNull AuthIssuerConfig authIssuerConfig,
                                   @NonNull Callbacks callbacks,
                                   @NonNull byte[] associationPublicKey,
                                   @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                                   @NonNull String scheme,
                                   @NonNull String relayDomain,
                                   @NonNull String dappNostrPubkey,
                                   @NonNull WalletIconProvider iconProvider) {
        super(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks,
                associationPublicKey, associationProtocolVersions, iconProvider);
        mScheme = scheme;
        mRelayDomain = relayDomain;
        mDappNostrPubkey = dappNostrPubkey;
        mNostrPrivateKey = NostrCrypto.generatePrivateKey();
        mSessionIdentifier = NostrCrypto.deriveSessionIdentifier(associationPublicKey);
    }

    @Override
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
                    mState = State.CLOSED;
                    destroyResourcesOnClose();
                    break;
                case CONNECTING:
                    mState = State.CLOSING;
                    notifySessionEstablishmentFailed("Scenario closed while connecting");
                    if (mNostrRelay != null) {
                        mNostrRelay.close();
                    } else {
                        mState = State.CLOSED;
                        destroyResourcesOnClose();
                    }
                    break;
                case AWAITING_HELLO_REQ:
                case ESTABLISHING_SESSION:
                    mState = State.CLOSING;
                    notifySessionEstablishmentFailed("Scenario closed during session establishment");
                    mNostrRelay.close();
                    break;
                case STARTED:
                    mState = State.CLOSING;
                    mNostrRelay.close();
                    break;
                case CLOSING:
                case CLOSED:
                    break;
                default:
                    throw new IllegalStateException("Error: attempt to close in an unknown state");
            }
        }
    }

    @Override
    public MessageReceiver createMessageReceiver() {
        return new MobileWalletAdapterSession(this,
                new MobileWalletAdapterServer(mMobileWalletAdapterConfig, mIoLooper, mMethodHandlers),
                mSessionStateCallbacks
        );
    }

    @GuardedBy("mLock")
    private void doTryConnect() {
        assert (mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;

        URI relayUri;
        try {
            relayUri = new URI(mScheme + "://" + mRelayDomain);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid relay domain: " + mRelayDomain, e);
            mState = State.CLOSED;
            destroyResourcesOnClose();
            notifySessionEstablishmentFailed("Invalid relay URI");
            return;
        }

        mNostrRelay = new NostrRelay(relayUri, mSessionIdentifier, mDappNostrPubkey,
                mNostrPrivateKey, createMessageReceiver(), mNostrRelayStateCallbacks,
                CONNECT_TIMEOUT_MS);
        mNostrRelay.connect();
    }

    @GuardedBy("mLock")
    private void doConnected() {
        assert (mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.v(TAG, "Connected to Nostr relay, CONNECT sent, awaiting dapp message");
        mState = State.AWAITING_HELLO_REQ;
        mConnectionBackoffExecutor.shutdownNow();
        mConnectionBackoffExecutor = null;
    }

    @GuardedBy("mLock")
    private void doConnectionFailed() {
        assert (mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        if (++mConnectionAttempts < CONNECT_MAX_ATTEMPTS) {
            final int delay = CONNECT_BACKOFF_SCHEDULE_MS[
                    Math.min(mConnectionAttempts, CONNECT_BACKOFF_SCHEDULE_MS.length - 1)];
            Log.d(TAG, "Connect attempt failed, retrying in " + delay + " ms");
            mNostrRelay = null;
            mConnectionBackoffExecutor.schedule(this::doTryConnect, delay, TimeUnit.MILLISECONDS);
        } else {
            Log.w(TAG, "Failed establishing Nostr relay connection");
            mState = State.CLOSED;
            destroyResourcesOnClose();
            notifySessionEstablishmentFailed("Unable to connect to Nostr relay");
        }
    }

    @GuardedBy("mLock")
    private void doSessionReady() {
        assert (mState == State.AWAITING_HELLO_REQ || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.v(TAG, "Nostr session ready, establishing MWA session");
        mState = State.ESTABLISHING_SESSION;
    }

    @GuardedBy("mLock")
    private void doSessionEstablished() {
        assert (mState == State.ESTABLISHING_SESSION || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.d(TAG, "Session established, scenario ready for use");
        mState = State.STARTED;
        notifySessionEstablishmentSucceeded();
        mCallbacks.onScenarioReady();
    }

    @GuardedBy("mLock")
    private void doDisconnected() {
        if (mState == State.CONNECTING || mState == State.AWAITING_HELLO_REQ
                || mState == State.ESTABLISHING_SESSION) {
            String message = "Disconnected before session established";
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
        mNostrRelay = null;
        if (mConnectionBackoffExecutor != null) {
            mConnectionBackoffExecutor.shutdownNow();
            mConnectionBackoffExecutor = null;
        }
    }

    @NonNull
    private final NostrRelay.StateCallbacks mNostrRelayStateCallbacks = new NostrRelay.StateCallbacks() {
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
                doSessionReady();
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
        NOT_STARTED, CONNECTING, AWAITING_HELLO_REQ, ESTABLISHING_SESSION, STARTED, CLOSING, CLOSED
    }
}
