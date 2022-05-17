/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.scenario;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.clientlib.transport.websockets.MobileWalletAdapterWebSocket;
import com.solana.mobilewalletadapter.common.AssociationContract;
import com.solana.mobilewalletadapter.common.WebSocketsTransportContract;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;

import java.net.URI;
import java.net.URISyntaxException;
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
    private MobileWalletAdapterClient mMobileWalletAdapterClient;

    public LocalAssociationScenario(@NonNull Looper looper, @Nullable Callbacks callbacks) {
        this(looper, callbacks, null);
    }

    public LocalAssociationScenario(@NonNull Looper looper,
                                    @Nullable Callbacks callbacks,
                                    @Nullable Uri endpointSpecificUriPrefix) {
        super(callbacks);

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
            mWebSocketUri = new URI("ws", null,
                    WebSocketsTransportContract.WEBSOCKETS_LOCAL_HOST, mPort,
                    WebSocketsTransportContract.WEBSOCKETS_LOCAL_PATH, null, null);
        } catch (URISyntaxException e) {
            throw new UnsupportedOperationException("Failed assembling a LocalAssociation URI", e);
        }

        mMobileWalletAdapterClient = new MobileWalletAdapterClient();
        mMobileWalletAdapterSession = new MobileWalletAdapterSession(
                mMobileWalletAdapterClient,
                mSessionStateCallbacks,
                MobileWalletAdapterSessionCommon.PayloadEncryptionMethod.AES128_GCM,
                false);

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
        dataUriBuilder
                .appendEncodedPath(AssociationContract.LOCAL_PATH_SUFFIX)
                .appendQueryParameter(AssociationContract.PARAMETER_ASSOCIATION_TOKEN,
                        mMobileWalletAdapterSession.encodeAssociationToken())
                .appendQueryParameter(AssociationContract.LOCAL_PARAMETER_PORT,
                        Integer.toString(mPort));

        return new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(dataUriBuilder.build());
    }

    @Override
    public void start() {
        if (mState != State.NOT_STARTED) {
            throw new IllegalStateException("Scenario has already been started");
        }

        mState = State.CONNECTING;

        // Delay the first connect to allow the association intent receiver to start the WebSocket
        // server
        mHandler.postDelayed(this::doConnect, CONNECT_BACKOFF_SCHEDULE_MS[0]);
    }

    @Override
    public void close() {
        switch (mState) {
            case NOT_STARTED:
                // If not started, just teardown immediately
                mState = State.CLOSED;
                destroyResources();
                if (mCallbacks != null) {
                    mCallbacks.onScenarioComplete();
                    mCallbacks.onScenarioTeardownComplete();
                }
                break;
            case CONNECTING:
                if (mMobileWalletAdapterWebSocket != null) {
                    mState = State.CLOSING;
                    mMobileWalletAdapterWebSocket.close();
                } else {
                    // In the middle of a backoff - we can tear down immediately
                    mState = State.CLOSED;
                    mHandler.removeCallbacksAndMessages(null); // pre-closed callbacks no longer relevant
                    destroyResources();
                    if (mCallbacks != null) {
                        mCallbacks.onScenarioComplete();
                        mCallbacks.onScenarioTeardownComplete();
                    }
                }
                break;
            case CONNECTED:
                mState = State.CLOSING;
                mMobileWalletAdapterWebSocket.close();
                break;
            case CLOSING:
            case CLOSED:
                // No-op; scenario is either already complete, or in the process of being torn down
                break;
        }
    }

    private void doConnect() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        mMobileWalletAdapterWebSocket = new MobileWalletAdapterWebSocket(mWebSocketUri,
                mMobileWalletAdapterSession, mWebSocketStateCallbacks, CONNECT_TIMEOUT_MS);
        mMobileWalletAdapterWebSocket.connect(); // [async]
    }

    private void doConnected() {
        assert(mState == State.CONNECTING || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        mState = State.CONNECTED;
        Log.v(TAG, "WebSocket connection established, waiting for session establishment");
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
            mHandler.postDelayed(this::doConnect, delay);
        } else {
            // We never connected, so we won't get an onConnectionClosed; do cleanup directly here
            mState = State.CLOSED;
            mHandler.removeCallbacksAndMessages(null); // pre-closed callbacks no longer relevant
            destroyResources();

            Log.w(TAG, "Failed establishing a WebSocket connection");
            if (mCallbacks != null) {
                mCallbacks.onScenarioError();
                mCallbacks.onScenarioTeardownComplete();
            }
        }
    }

    private void doDisconnected() {
        assert(mState == State.CONNECTING || mState == State.CONNECTED || mState == State.CLOSING);
        if (mState != State.CLOSING) {
            // Disconnecting before session establishment completed
            mState = State.CLOSING;
            Log.d(TAG, "Session terminated before session established");
            if (mCallbacks != null) mCallbacks.onScenarioComplete();
        }
        mState = State.CLOSED;
        mHandler.removeCallbacksAndMessages(null); // pre-closed callbacks no longer relevant
        destroyResources();

        Log.i(TAG, "Session cleanup complete");
        if (mCallbacks != null) mCallbacks.onScenarioTeardownComplete();
    }

    private void doSessionEstablished() {
        assert(mState == State.CONNECTED || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        Log.d(TAG, "Session established, scenario ready for use");
        if (mCallbacks != null) mCallbacks.onScenarioReady(mMobileWalletAdapterClient);
    }

    private void doSessionClosed() {
        assert(mState == State.CONNECTED || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        mState = State.CLOSING;
        Log.d(TAG, "Session terminated normally");
        if (mCallbacks != null) mCallbacks.onScenarioComplete();
        // doDisconnected is responsible for connection cleanup
    }

    private void doSessionError() {
        assert(mState == State.CONNECTED || mState == State.CLOSING);
        if (mState == State.CLOSING) return;
        mState = State.CLOSING;
        Log.w(TAG, "Session error, terminating");
        if (mCallbacks != null) mCallbacks.onScenarioError();
        // doDisconnected is responsible for connection cleanup
    }

    private void destroyResources() {
        mMobileWalletAdapterSession = null;
        mMobileWalletAdapterWebSocket = null;
        mMobileWalletAdapterClient = null;
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
        NOT_STARTED, CONNECTING, CONNECTED, CLOSING, CLOSED
    }
}
