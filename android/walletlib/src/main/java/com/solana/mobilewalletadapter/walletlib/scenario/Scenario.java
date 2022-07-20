/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRecord;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRepository;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.walletlib.util.LooperThread;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Scenario {
    private static final String TAG = Scenario.class.getSimpleName();

    @NonNull
    public final byte[] associationPublicKey;

    @NonNull
    protected final MobileWalletAdapterConfig mMobileWalletAdapterConfig;
    @NonNull
    protected final Looper mIoLooper;
    @NonNull
    protected final Handler mIoHandler;
    @NonNull
    protected final Callbacks mCallbacks;
    @NonNull
    protected final AuthRepository mAuthRepository;

    protected Scenario(@NonNull Context context,
                       @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                       @NonNull AuthIssuerConfig authIssuerConfig,
                       @NonNull Callbacks callbacks,
                       @NonNull byte[] associationPublicKey)
    {
        mCallbacks = callbacks;
        mMobileWalletAdapterConfig = mobileWalletAdapterConfig;
        this.associationPublicKey = associationPublicKey;

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
                new MobileWalletAdapterServer(mMobileWalletAdapterConfig, mIoLooper, mMethodHandlers),
                mSessionStateCallbacks);
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

            final NotifyingCompletableFuture<Boolean> future = new NotifyingCompletableFuture<>();
            future.notifyOnComplete(f -> {
                try {
                    final Boolean reauthorize = f.get(); // won't block
                    if (!reauthorize) {
                        mIoHandler.post(() -> request.completeExceptionally(
                                new MobileWalletAdapterServer.RequestDeclinedException(
                                        "app declined reauthorization request")));
                        mAuthRepository.revoke(authRecord);
                        return;
                    }

                    final AuthRecord reissuedAuthRecord = mAuthRepository.reissue(authRecord);
                    if (reissuedAuthRecord == null) {
                        // No need to explicitly revoke the old auth token; that is part of the
                        // reissue method contract
                        mIoHandler.post(() -> request.completeExceptionally(
                                new MobileWalletAdapterServer.RequestDeclinedException(
                                        "auth_token not valid for reissue")));
                        return;
                    }

                    final String authToken;
                    if (reissuedAuthRecord == authRecord) {
                        // Reissued same auth record; don't regenerate the token
                        authToken = request.authToken;
                    } else {
                        authToken = mAuthRepository.toAuthToken(reissuedAuthRecord);
                    }

                    mIoHandler.post(() -> request.complete(
                            new MobileWalletAdapterServer.ReauthorizeResult(authToken)));
                } catch (ExecutionException e) {
                    throw new RuntimeException("Unexpected exception while waiting for reauthorization", e);
                } catch (InterruptedException | CancellationException e) {
                    request.cancel(true);
                }
            });

            mIoHandler.post(() -> mCallbacks.onReauthorizeRequest(
                    new ReauthorizeRequest(future, request.identityName, request.identityUri,
                            request.iconUri, authRecord.scope)));
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
        public void signPayloads(@NonNull MobileWalletAdapterServer.SignPayloadsRequest request) {
            final AuthRecord authRecord;
            try {
                authRecord = authTokenToAuthRecord(request.authToken);
            } catch (MobileWalletAdapterServer.MobileWalletAdapterServerException e) {
                mIoHandler.post(() -> request.completeExceptionally(e));
                return;
            }

            final Runnable r;
            switch (request.type) {
                case Transaction:
                    r = () -> mCallbacks.onSignTransactionsRequest(new SignTransactionsRequest(
                            request, authRecord.identity.name, authRecord.identity.uri,
                            authRecord.identity.relativeIconUri, authRecord.scope,
                            authRecord.publicKey));
                    break;
                case Message:
                    r = () -> mCallbacks.onSignMessagesRequest(new SignMessagesRequest(request,
                            authRecord.identity.name, authRecord.identity.uri,
                            authRecord.identity.relativeIconUri, authRecord.scope,
                            authRecord.publicKey));
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown payload type");
            }
            mIoHandler.post(r);
        }

        @Override
        public void signAndSendTransactions(
                @NonNull MobileWalletAdapterServer.SignAndSendTransactionsRequest request) {
            final AuthRecord authRecord;
            try {
                authRecord = authTokenToAuthRecord(request.authToken);
            } catch (MobileWalletAdapterServer.MobileWalletAdapterServerException e) {
                mIoHandler.post(() -> request.completeExceptionally(e));
                return;
            }

            mIoHandler.post(() -> mCallbacks.onSignAndSendTransactionsRequest(
                    new SignAndSendTransactionsRequest(request, authRecord.identity.name,
                            authRecord.identity.uri, authRecord.identity.relativeIconUri,
                            authRecord.scope, authRecord.publicKey)));
        }

        @NonNull
        private AuthRecord authTokenToAuthRecord(@NonNull String authToken)
                throws MobileWalletAdapterServer.MobileWalletAdapterServerException{
            final AuthRecord authRecord = mAuthRepository.fromAuthToken(authToken);

            if (authRecord == null || !authRecord.isAuthorized()) {
                throw new MobileWalletAdapterServer.AuthTokenNotValidException("auth_token not valid for this request");
            } else if (authRecord.isExpired()) {
                throw new MobileWalletAdapterServer.ReauthorizationRequiredException("auth_token requires reauthorization");
            }

            return authRecord;
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
        void onReauthorizeRequest(@NonNull ReauthorizeRequest request);
        void onSignTransactionsRequest(@NonNull SignTransactionsRequest request);
        void onSignMessagesRequest(@NonNull SignMessagesRequest request);
        void onSignAndSendTransactionsRequest(@NonNull SignAndSendTransactionsRequest request);
    }
}