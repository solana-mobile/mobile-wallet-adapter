/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    private final Object mLock = new Object();
    @Nullable
    @GuardedBy("mLock")
    private AuthRecord mActiveAuthorization = null;

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

    private final MobileWalletAdapterServer.MethodHandlers mMethodHandlers =
            new MobileWalletAdapterServer.MethodHandlers() {
        @Override
        public void authorize(@NonNull MobileWalletAdapterServer.AuthorizeRequest request) {
            final NotifyingCompletableFuture<AuthorizeRequest.Result> future = new NotifyingCompletableFuture<>();
            future.notifyOnComplete(f -> mIoHandler.post(() -> { // Note: run in IO thread context
                try {
                    final AuthorizeRequest.Result authorize = f.get(); // won't block

                    if (authorize != null) {
                        final String name = request.identityName != null ? request.identityName : "";
                        final Uri uri = request.identityUri != null ? request.identityUri : Uri.EMPTY;
                        final Uri relativeIconUri = request.iconUri != null ? request.iconUri : Uri.EMPTY;
                        final AuthRecord authRecord = mAuthRepository.issue(
                                name, uri, relativeIconUri, authorize.publicKey, authorize.scope);
                        Log.d(TAG, "Authorize request completed successfully; issued auth: " + authRecord);
                        synchronized (mLock) {
                            mActiveAuthorization = authRecord;
                        }

                        final String authToken = mAuthRepository.toAuthToken(authRecord);
                        request.complete(new MobileWalletAdapterServer.AuthorizeResult(
                                authToken, authorize.publicKey, authorize.walletUriBase));
                    } else {
                        synchronized (mLock) {
                            mActiveAuthorization = null;
                        }
                        request.completeExceptionally(new MobileWalletAdapterServer.RequestDeclinedException(
                                "authorize request declined"));
                    }
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException("Unexpected exception while waiting for authorization", e);
                } catch (CancellationException e) {
                    synchronized (mLock) {
                        mActiveAuthorization = null;
                    }
                    request.cancel(true);
                }
            }));

            mIoHandler.post(() -> mCallbacks.onAuthorizeRequest(new AuthorizeRequest(
                    future, request.identityName, request.identityUri, request.iconUri)));
        }

        @Override
        public void reauthorize(@NonNull MobileWalletAdapterServer.ReauthorizeRequest request) {
            final AuthRecord authRecord = mAuthRepository.fromAuthToken(request.authToken);
            if (authRecord == null) {
                synchronized (mLock) {
                    mActiveAuthorization = null;
                }
                mIoHandler.post(() -> request.completeExceptionally(
                        new MobileWalletAdapterServer.AuthorizationNotValidException(
                                "auth_token not valid for this request")));
                return;
            }

            final NotifyingCompletableFuture<Boolean> future = new NotifyingCompletableFuture<>();
            future.notifyOnComplete(f -> mIoHandler.post(() -> { // Note: run in IO thread context
                try {
                    final Boolean reauthorize = f.get(); // won't block
                    if (!reauthorize) {
                        synchronized (mLock) {
                            mActiveAuthorization = null;
                        }
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
                        synchronized (mLock) {
                            mActiveAuthorization = null;
                        }
                        mIoHandler.post(() -> request.completeExceptionally(
                                new MobileWalletAdapterServer.RequestDeclinedException(
                                        "auth_token not valid for reissue")));
                        return;
                    }
                    synchronized (mLock) {
                        mActiveAuthorization = reissuedAuthRecord;
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
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException("Unexpected exception while waiting for reauthorization", e);
                } catch (CancellationException e) {
                    synchronized (mLock) {
                        mActiveAuthorization = null;
                    }
                    request.cancel(true);
                }
            }));

            mIoHandler.post(() -> mCallbacks.onReauthorizeRequest(new ReauthorizeRequest(
                    future, request.identityName, request.identityUri, request.iconUri,
                    authRecord.scope)));
        }

        @Override
        public void deauthorize(@NonNull MobileWalletAdapterServer.DeauthorizeRequest request) {
            final AuthRecord authRecord = mAuthRepository.fromAuthToken(request.authToken);
            if (authRecord != null) {
                mAuthRepository.revoke(authRecord);
            }
            synchronized (mLock) {
                if (mActiveAuthorization == authRecord) {
                    mActiveAuthorization = null;
                }
            }
            mIoHandler.post(() -> request.complete(null));
        }

        @Override
        public void signPayloads(@NonNull MobileWalletAdapterServer.SignPayloadsRequest request) {
            final AuthRecord authRecord;
            synchronized (mLock) {
                authRecord = mActiveAuthorization;
            }
            if (authRecord == null || authRecord.isRevoked()) {
                mIoHandler.post(() -> request.completeExceptionally(
                        new MobileWalletAdapterServer.AuthorizationNotValidException("Session not authorized for privileged requests")));
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
            synchronized (mLock) {
                authRecord = mActiveAuthorization;
            }
            if (authRecord == null || authRecord.isRevoked()) {
                mIoHandler.post(() -> request.completeExceptionally(
                        new MobileWalletAdapterServer.AuthorizationNotValidException("Session not authorized for privileged requests")));
                return;
            }

            mIoHandler.post(() -> mCallbacks.onSignAndSendTransactionsRequest(
                    new SignAndSendTransactionsRequest(request, authRecord.identity.name,
                            authRecord.identity.uri, authRecord.identity.relativeIconUri,
                            authRecord.scope, authRecord.publicKey)));
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