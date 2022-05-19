/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.common.protocol.PrivilegedMethod;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRecord;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRepository;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.walletlib.util.LooperThread;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class Scenario {
    private static final String TAG = Scenario.class.getSimpleName();

    @NonNull
    public final String associationToken;

    @NonNull
    protected final Looper mIoLooper;
    @NonNull
    protected final Handler mIoHandler;
    @NonNull
    protected final Callbacks mCallbacks;
    @NonNull
    protected final AuthRepository mAuthRepository;

    protected Scenario(@NonNull Context context,
                       @NonNull AuthIssuerConfig authIssuerConfig,
                       @NonNull Callbacks callbacks,
                       @NonNull String associationToken)
    {
        mCallbacks = callbacks;
        this.associationToken = associationToken;

        final LooperThread t = new LooperThread();
        t.start();
        mIoLooper = t.getLooper(); // blocks until Looper is available
        mIoHandler = new Handler(mIoLooper);

        mAuthRepository = new AuthRepository(context, authIssuerConfig);
    }

    @Override
    protected void finalize() {
        mIoLooper.quitSafely();
    }

    public MessageReceiver createMessageReceiver() {
        return new MobileWalletAdapterSession(
                this,
                new MobileWalletAdapterServer(mIoLooper, mMethodHandlers),
                mSessionStateCallbacks,
                null, // use whatever the client specifies
                false);
    }

    public abstract void start();
    public abstract void close();

    private final MobileWalletAdapterSessionCommon.StateCallbacks mSessionStateCallbacks =
            new MobileWalletAdapterSessionCommon.StateCallbacks() {
        private final AtomicInteger mClientCount = new AtomicInteger();

        @Override
        public void onSessionEstablished() {
            Log.d(TAG, "MobileWalletAdapter session established");
            if (mClientCount.incrementAndGet() == 1) {
                mIoHandler.post(mAuthRepository::start);
                mIoHandler.post(mCallbacks::onScenarioServingClients);
            }
        }

        @Override
        public void onSessionClosed() {
            Log.d(TAG, "MobileWalletAdapter session terminated");
            if (mClientCount.decrementAndGet() == 0) {
                mIoHandler.post(mCallbacks::onScenarioServingComplete);
                mIoHandler.post(mAuthRepository::stop);
            }
        }

        @Override
        public void onSessionError() {
            Log.w(TAG, "MobileWalletAdapter session error");
            if (mClientCount.decrementAndGet() == 0) {
                mIoHandler.post(mCallbacks::onScenarioServingComplete);
                mIoHandler.post(mAuthRepository::stop);
            }
        }
    };

    private final MobileWalletAdapterServer.MethodHandlers mMethodHandlers =
            new MobileWalletAdapterServer.MethodHandlers() {
        @Override
        public void authorize(@NonNull MobileWalletAdapterServer.AuthorizeRequest request) {
            mIoHandler.post(() -> mCallbacks.onAuthorizeRequest(
                    new AuthorizeRequest(mIoHandler, mAuthRepository, request)));
        }

        @Override
        public void reauthorize(@NonNull MobileWalletAdapterServer.ReauthorizeRequest request) {
            final AuthRecord authRecord = mAuthRepository.fromAuthToken(request.authToken);
            if (authRecord == null) {
                mIoHandler.post(() -> request.completeExceptionally(
                        new MobileWalletAdapterServer.AuthTokenNotValidException(
                                "auth_token not valid for this request")));
                return;
            }

            final AuthRecord reissuedAuthRecord = mAuthRepository.reissue(authRecord);
            if (reissuedAuthRecord == null) {
                mIoHandler.post(() -> request.completeExceptionally(
                        new MobileWalletAdapterServer.RequestDeclinedException(
                                "auth_token not valid for reissue")));
                return;
            }

            mIoHandler.post(() -> request.complete(new MobileWalletAdapterServer.ReauthorizeResult(
                    mAuthRepository.toAuthToken(reissuedAuthRecord))));
        }

        @Override
        public void deauthorize(@NonNull MobileWalletAdapterServer.DeauthorizeRequest request) {
            final AuthRecord authRecord = mAuthRepository.fromAuthToken(request.authToken);
            if (authRecord != null) {
                mAuthRepository.revoke(authRecord);
            }
            mIoHandler.post(() -> request.complete(null));
        }

        @Override
        public void signPayload(@NonNull MobileWalletAdapterServer.SignPayloadRequest request) {
            final PrivilegedMethod method;
            final Runnable r;
            switch (request.type) {
                case Transaction:
                    method = PrivilegedMethod.SignTransaction;
                    r = () -> mCallbacks.onSignTransactionRequest(new SignTransactionRequest(request));
                    break;
                case Message:
                    method = PrivilegedMethod.SignMessage;
                    r = () -> mCallbacks.onSignMessageRequest(new SignMessageRequest(request));
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown payload type");
            }

            final MobileWalletAdapterServer.MobileWalletAdapterServerException ex =
                    validateAuthTokenForPrivilegedMethod(request.authToken, method);
            if (ex != null) {
                mIoHandler.post(() -> request.completeExceptionally(ex));
            } else {
                mIoHandler.post(r);
            }
        }

        @Override
        public void signAndSendTransaction(
                @NonNull MobileWalletAdapterServer.SignAndSendTransactionRequest request) {
            final MobileWalletAdapterServer.MobileWalletAdapterServerException ex =
                    validateAuthTokenForPrivilegedMethod(request.authToken,
                            PrivilegedMethod.SignAndSendTransaction);
            if (ex != null) {
                mIoHandler.post(() -> request.completeExceptionally(ex));
            } else {
                mIoHandler.post(() -> mCallbacks.onSignAndSendTransactionRequest(
                        new SignAndSendTransactionRequest(request)));
            }
        }

        @Nullable
        private MobileWalletAdapterServer.MobileWalletAdapterServerException validateAuthTokenForPrivilegedMethod(
                @NonNull String authToken, @NonNull PrivilegedMethod privilegedMethod) {
            final AuthRecord authRecord = mAuthRepository.fromAuthToken(authToken);
            final MobileWalletAdapterServer.MobileWalletAdapterServerException ex;
            if (authRecord == null || !authRecord.isAuthorized(privilegedMethod)) {
                ex = new MobileWalletAdapterServer.AuthTokenNotValidException("auth_token not valid for this request");
            } else if (authRecord.isExpired()) {
                ex = new MobileWalletAdapterServer.ReauthorizationRequiredException("auth_token requires reauthorization");
            } else {
                ex = null;
            }
            return ex;
        }
    };

    public interface Callbacks {
        // Scenario state callbacks
        void onScenarioReady();
        void onScenarioServingClients();
        void onScenarioServingComplete();
        void onScenarioComplete();
        void onScenarioError();
        void onScenarioTeardownComplete();

        // Request callbacks
        void onAuthorizeRequest(@NonNull AuthorizeRequest request);
        void onSignTransactionRequest(@NonNull SignTransactionRequest request);
        void onSignMessageRequest(@NonNull SignMessageRequest request);
        void onSignAndSendTransactionRequest(@NonNull SignAndSendTransactionRequest request);
    }
}