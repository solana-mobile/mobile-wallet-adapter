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
            // Clear the active auth token immediately upon beginning authorization; it will be
            // set to valid only on successful completion
            synchronized (mLock) {
                mActiveAuthorization = null;
            }

            final NotifyingCompletableFuture<AuthorizeRequest.Result> future = new NotifyingCompletableFuture<>();
            future.notifyOnComplete(f -> mIoHandler.post(() -> { // Note: run in IO thread context
                try {
                    final AuthorizeRequest.Result authorize = f.get(); // won't block

                    if (authorize != null) {
                        final String name = request.identityName != null ? request.identityName : "";
                        final Uri uri = request.identityUri != null ? request.identityUri : Uri.EMPTY;
                        final Uri relativeIconUri = request.iconUri != null ? request.iconUri : Uri.EMPTY;
                        final AuthRecord authRecord = mAuthRepository.issue(
                                name, uri, relativeIconUri, authorize.publicKey,
                                authorize.accountLabel, request.cluster, authorize.walletUriBase,
                                authorize.scope);
                        Log.d(TAG, "Authorize request completed successfully; issued auth: " + authRecord);
                        synchronized (mLock) {
                            mActiveAuthorization = authRecord;
                        }

                        final String authToken = mAuthRepository.toAuthToken(authRecord);
                        request.complete(new MobileWalletAdapterServer.AuthorizationResult(
                                authToken, authorize.publicKey, authorize.accountLabel,
                                authorize.walletUriBase));
                    } else {
                        request.completeExceptionally(new MobileWalletAdapterServer.RequestDeclinedException(
                                "authorize request declined"));
                    }
                } catch (ExecutionException e) {
                    final Throwable cause = e.getCause();
                    assert(cause instanceof Exception); // expected to always be an Exception
                    request.completeExceptionally((Exception)cause);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Unexpected interruption while waiting for authorization", e);
                } catch (CancellationException e) {
                    request.cancel(true);
                }
            }));

            mIoHandler.post(() -> mCallbacks.onAuthorizeRequest(new AuthorizeRequest(
                    future, request.identityName, request.identityUri, request.iconUri,
                    request.cluster)));
        }

        @Override
        public void reauthorize(@NonNull MobileWalletAdapterServer.ReauthorizeRequest request) {
            // Clear the active auth token immediately upon beginning reauthorization; it will be
            // set to valid only on successful completion
            synchronized (mLock) {
                mActiveAuthorization = null;
            }

            final AuthRecord authRecord = mAuthRepository.fromAuthToken(request.authToken);
            if (authRecord == null) {
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
                            new MobileWalletAdapterServer.AuthorizationResult(
                                    authToken, authRecord.publicKey, authRecord.accountLabel,
                                    authRecord.walletUriBase)));
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException("Unexpected exception while waiting for reauthorization", e);
                } catch (CancellationException e) {
                    request.cancel(true);
                }
            }));

            mIoHandler.post(() -> mCallbacks.onReauthorizeRequest(new ReauthorizeRequest(
                    future, request.identityName, request.identityUri, request.iconUri,
                    authRecord.cluster, authRecord.scope)));
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
        public void signTransactions(@NonNull MobileWalletAdapterServer.SignTransactionsRequest request) {
            final AuthRecord authRecord;
            synchronized (mLock) {
                authRecord = mActiveAuthorization;
            }
            if (authRecord == null || authRecord.isRevoked()) {
                mIoHandler.post(() -> request.completeExceptionally(
                        new MobileWalletAdapterServer.AuthorizationNotValidException("Session not authorized for privileged requests")));
                return;
            }

            mIoHandler.post(() -> mCallbacks.onSignTransactionsRequest(new SignTransactionsRequest(
                    request, authRecord.identity.name, authRecord.identity.uri,
                    authRecord.identity.relativeIconUri, authRecord.scope,
                    authRecord.publicKey, authRecord.cluster)));
        }

        @Override
        public void signMessages(@NonNull MobileWalletAdapterServer.SignMessagesRequest request) {
            final AuthRecord authRecord;
            synchronized (mLock) {
                authRecord = mActiveAuthorization;
            }
            if (authRecord == null || authRecord.isRevoked()) {
                mIoHandler.post(() -> request.completeExceptionally(
                        new MobileWalletAdapterServer.AuthorizationNotValidException("Session not authorized for privileged requests")));
                return;
            }

            try {
                final SignMessagesRequest smr = new SignMessagesRequest(request,
                        authRecord.identity.name, authRecord.identity.uri,
                        authRecord.identity.relativeIconUri, authRecord.scope,
                        authRecord.publicKey, authRecord.cluster);
                mIoHandler.post(() -> mCallbacks.onSignMessagesRequest(smr));
            } catch (IllegalArgumentException e) {
                mIoHandler.post(() -> request.completeExceptionally(
                        new MobileWalletAdapterServer.RequestDeclinedException("Unexpected address; not signing message"))); // TODO(#44): support multiple addresses
            }
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
                            authRecord.scope, authRecord.publicKey, authRecord.cluster)));
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