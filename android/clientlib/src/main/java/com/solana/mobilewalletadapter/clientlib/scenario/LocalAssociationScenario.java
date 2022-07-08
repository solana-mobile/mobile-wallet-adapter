/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.scenario;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.clientlib.transport.websockets.MobileWalletAdapterWebSocket;
import com.solana.mobilewalletadapter.common.AssociationContract;
import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Random;

public class LocalAssociationScenario extends Scenario {
    private static final String TAG = LocalAssociationScenario.class.getSimpleName();
    private static final int CONNECT_MAX_ATTEMPTS = 6;
    private static final int[] CONNECT_BACKOFF_SCHEDULE_MS = { 100, 250, 500, 750 };
    private static final int CONNECT_TIMEOUT_MS = 200; // localhost connections should be very fast

    @NonNull
    private final Handler mHandler;
    private final Uri mEndpointSpecificUriPrefix;
    @WebSocketsTransportContract.LocalPortRange
    private final int mPort;
    @NonNull
    private final URI mWebSocketUri;

    private State mState = State.NOT_STARTED;
    private int mConnectionAttempts = 0;
    private MobileWalletAdapterSession mMobileWalletAdapterSession;
    private MobileWalletAdapterWebSocket mMobileWalletAdapterWebSocket;
    private NotifyingCompletableFuture<MobileWalletAdapterClient> mSessionEstablishedFuture; // valid in State.CONNECTING and State.ESTABLISHING_SESSION
    private ArrayList<NotifyingCompletableFuture<Void>> mClosedFuture; // _may_ be valid in State.CLOSING

    public LocalAssociationScenario(@NonNull Looper looper,
                                    @IntRange(from = 0) int clientTimeoutMs,
                                    @Nullable Uri endpointSpecificUriPrefix) {
        super(clientTimeoutMs);

        if (endpointSpecificUriPrefix != null && (!endpointSpecificUriPrefix.isAbsolute() ||
                !endpointSpecificUriPrefix.isHierarchical())) {
            throw new IllegalArgumentException("Endpoint-specific URI prefix must be absolute and hierarchical");
        }

        mHandler = new Handler(looper);
        mEndpointSpecificUriPrefix = endpointSpecificUriPrefix;
        mPort = new Random().nextInt(WebSocketsTransportContract.WEBSOCKETS_LOCAL_PORT_MAX -
                WebSocketsTransportContract.WEBSOCKETS_LOCAL_PORT_MIN + 1) +
                WebSocketsTransportContract.WEBSOCKETS_LOCAL_PORT_MIN;
        try {
            mWebSocketUri = new URI(WebSocketsTransportContract.WEBSOCKETS_LOCAL_SCHEME, null,
                    WebSocketsTransportContract.WEBSOCKETS_LOCAL_HOST, mPort,
                    WebSocketsTransportContract.WEBSOCKETS_LOCAL_PATH, null, null);
        } catch (URISyntaxException e) {
            throw new UnsupportedOperationException("Failed assembling a LocalAssociation URI", e);
        }

        mMobileWalletAdapterSession = new MobileWalletAdapterSession(
                mMobileWalletAdapterClient,
                mSessionStateCallbacks);

        Log.v(TAG, "Creating local association scenario for " + mWebSocketUri);
    }

    @NonNull
    public Intent createAssociationIntent() {
        final Uri.Builder dataUriBuilder;
        if (mEndpointSpecificUriPrefix != null) {
            dataUriBuilder = mEndpointSpecificUriPrefix.buildUpon()
                    .clearQuery()
                    .fragment(null);
        } else {
            dataUriBuilder = new Uri.Builder()
                    .scheme(AssociationContract.SCHEME_MOBILE_WALLET_ADAPTER);
        }
        final byte[] associationPublicKey = mMobileWalletAdapterSession.getEncodedAssociationPublicKey();
        final String associationToken = Base64.encodeToString(associationPublicKey,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        dataUriBuilder
                .appendEncodedPath(AssociationContract.LOCAL_PATH_SUFFIX)
                .appendQueryParameter(AssociationContract.PARAMETER_ASSOCIATION_TOKEN,
                        associationToken)
                .appendQueryParameter(AssociationContract.LOCAL_PARAMETER_PORT,
                        Integer.toString(mPort));

        return new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(dataUriBuilder.build());
    }

    @Override
    public NotifyOnCompleteFuture<MobileWalletAdapterClient> start() {
        if (mState != State.NOT_STARTED) {
            throw new IllegalStateException("Scenario has already been started");
        }

        mState = State.CONNECTING;
        final NotifyingCompletableFuture<MobileWalletAdapterClient> future = startDeferredFuture();

        // Delay the first connect to allow the association intent receiver to start the WebSocket
        // server
        mHandler.postDelayed(this::doTryConnect, CONNECT_BACKOFF_SCHEDULE_MS[0]);

        return future;
    }

    @Override
    public NotifyOnCompleteFuture<Void> close() {
        final NotifyingCompletableFuture<Void> future;
        switch (mState) {
            case NOT_STARTED:
                // If not started, just teardown immediately
                mState = State.CLOSED;
                destroyResources();
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
                    mHandler.removeCallbacksAndMessages(null); // pre-closed callbacks no longer relevant
                    destroyResources();
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

        return future;
    }

    private NotifyingCompletableFuture<MobileWalletAdapterClient> startDeferredFuture() {
        assert(mState == State.CONNECTING && mSessionEstablishedFuture == null);
        final NotifyingCompletableFuture<MobileWalletAdapterClient> future = new NotifyingCompletableFuture<>();
        mSessionEstablishedFuture = future;
        return future;
    }

    private NotifyingCompletableFuture<Void> closedImmediatelyFuture() {
        assert(mState == State.CLOSED);
        final NotifyingCompletableFuture<Void> future = new NotifyingCompletableFuture<>();
        future.complete(null);
        return future;
    }

    private NotifyingCompletableFuture<Void> closeDeferredFuture() {
        assert(mState == State.CLOSING);
        final NotifyingCompletableFuture<Void> future = new NotifyingCompletableFuture<>();
        if (mClosedFuture == null) {
            mClosedFuture = new ArrayList<>(1);
        }
        mClosedFuture.add(future);
        return future;
    }

    private void doTryConnect() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        mMobileWalletAdapterWebSocket = new MobileWalletAdapterWebSocket(mWebSocketUri,
                mMobileWalletAdapterSession, mWebSocketStateCallbacks, CONNECT_TIMEOUT_MS);
        mMobileWalletAdapterWebSocket.connect(); // [async]
    }

    private void doConnected() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.v(TAG, "WebSocket connection established, waiting for session establishment");
        mState = State.ESTABLISHING_SESSION;
    }

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
            mHandler.postDelayed(this::doTryConnect, delay);
        } else {
            Log.w(TAG, "Failed establishing a WebSocket connection");

            // We never connected, so we won't get an onConnectionClosed; do cleanup directly here
            mState = State.CLOSED;
            mHandler.removeCallbacksAndMessages(null); // pre-closed callbacks no longer relevant
            destroyResources();
            notifySessionEstablishmentFailed("Unable to connect to websocket server");
        }
    }

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
        mHandler.removeCallbacksAndMessages(null); // pre-closed callbacks no longer relevant
        destroyResources();
        notifyCloseCompleted();
    }

    private void doSessionEstablished() {
        assert(mState == State.ESTABLISHING_SESSION || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.d(TAG, "Session established, scenario ready for use");
        mState = State.STARTED;
        notifySessionEstablishmentSucceeded();
    }

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

    private void destroyResources() {
        mMobileWalletAdapterSession = null;
        mMobileWalletAdapterWebSocket = null;
    }

    private void notifySessionEstablishmentSucceeded() {
        mSessionEstablishedFuture.complete(mMobileWalletAdapterClient);
        mSessionEstablishedFuture = null;
    }

    private void notifySessionEstablishmentFailed(@NonNull String message) {
        mSessionEstablishedFuture.completeExceptionally(new ConnectionFailedException(message));
        mSessionEstablishedFuture = null;
    }

    private void notifyCloseCompleted() {
        // NOTE: if close was initiated by counterparty, there won't be a closed future to complete
        if (mClosedFuture != null) {
            for (NotifyingCompletableFuture<Void> future : mClosedFuture) {
                future.complete(null);
            }
            mClosedFuture = null;
        }
    }

    private final MobileWalletAdapterWebSocket.StateCallbacks mWebSocketStateCallbacks = new MobileWalletAdapterWebSocket.StateCallbacks() {
        @Override
        public void onConnected() { mHandler.post(() -> doConnected()); }

        @Override
        public void onConnectionFailed() { mHandler.post(() -> doConnectionFailed()); }

        @Override
        public void onConnectionClosed() { mHandler.post(() -> doDisconnected()); }
    };

    private final MobileWalletAdapterSessionCommon.StateCallbacks mSessionStateCallbacks = new MobileWalletAdapterSessionCommon.StateCallbacks() {
        @Override
        public void onSessionEstablished() { mHandler.post(() -> doSessionEstablished()); }

        @Override
        public void onSessionError() { mHandler.post(() -> doSessionError()); }

        @Override
        public void onSessionClosed() { mHandler.post(() -> doSessionClosed()); }
    };

    private enum State {
        NOT_STARTED, CONNECTING, ESTABLISHING_SESSION, STARTED, CLOSING, CLOSED
    }

    public static class ConnectionFailedException extends RuntimeException {
        public ConnectionFailedException(@NonNull String message) { super(message); }
    }
}
