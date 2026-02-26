/*
 * Copyright (c) 2024 Solana Mobile Inc.
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

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.SessionProperties;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRecord;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRepository;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRepositoryImpl;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;
import com.solana.mobilewalletadapter.walletlib.util.LooperThread;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public abstract class BaseScenario implements Scenario {
    private static final String TAG = BaseScenario.class.getSimpleName();

    @NonNull
    final public byte[] associationPublicKey;
    @NonNull
    final public List<SessionProperties.ProtocolVersion> associationProtocolVersions;

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

    protected final Object mLock = new Object();
    @Nullable
    @GuardedBy("mLock")
    protected AuthRecord mActiveAuthorization = null;
    @Nullable
    @GuardedBy("mLock")
    protected String mActiveSessionId = null;
    @Nullable
    @GuardedBy("mLock")
    private NotifyingCompletableFuture<String> mSessionEstablishedFuture = null;

    private final Uri mWalletIcon;

    protected BaseScenario(@NonNull Context context,
                           @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                           @NonNull AuthIssuerConfig authIssuerConfig,
                           @NonNull LocalScenario.Callbacks callbacks,
                           @NonNull byte[] associationPublicKey) {
        this(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks,
                associationPublicKey, List.of());
    }

    /*package*/ BaseScenario(@NonNull Context context,
                             @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                             @NonNull AuthIssuerConfig authIssuerConfig,
                             @NonNull Callbacks callbacks,
                             @NonNull byte[] associationPublicKey,
                             @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions) {
        this(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey,
                associationProtocolVersions, new DefaultWalletIconProvider(context));
    }

    /*package*/ BaseScenario(@NonNull Context context,
                             @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                             @NonNull AuthIssuerConfig authIssuerConfig,
                             @NonNull Callbacks callbacks,
                             @NonNull byte[] associationPublicKey,
                             @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                             @NonNull WalletIconProvider iconProvider) {
        mCallbacks = callbacks;
        mMobileWalletAdapterConfig = mobileWalletAdapterConfig;
        this.associationProtocolVersions = associationProtocolVersions;
        this.associationPublicKey = associationPublicKey;

        final LooperThread t = new LooperThread();
        t.start();
        mIoLooper = t.getLooper(); // blocks until Looper is available
        mIoHandler = new Handler(mIoLooper);

        mAuthRepository = new AuthRepositoryImpl(context, authIssuerConfig);

        Uri walletIcon = iconProvider.getWalletIconDataUri();
        if (walletIcon == null ||
                (walletIcon.getScheme() != null && walletIcon.getScheme().equals("data"))) {
            this.mWalletIcon = walletIcon;
        } else {
            throw new IllegalArgumentException("wallet icon provider returned an invalid icon URI: " +
                    "the wallet icon must be a data URI");
        }
    }

    @Override
    public byte[] getAssociationPublicKey() {
        return associationPublicKey;
    }

    @Override
    @NonNull
    public List<SessionProperties.ProtocolVersion> getAssociationProtocolVersions() {
        return associationProtocolVersions;
    }

    @Override
    protected void finalize() {
        mIoLooper.quitSafely();
    }

    @NonNull
    @GuardedBy("mLock")
    private NotifyingCompletableFuture<String> startDeferredFuture() {
        assert mSessionEstablishedFuture == null;
        final NotifyingCompletableFuture<String> future = new NotifyingCompletableFuture<>();
        mSessionEstablishedFuture = future;
        return future;
    }

    @GuardedBy("mLock")
    protected void notifySessionEstablishmentSucceeded() {
        assert (mActiveSessionId == null && mSessionEstablishedFuture != null);
        mActiveSessionId = UUID.randomUUID().toString();
        mSessionEstablishedFuture.complete(mActiveSessionId);
        mSessionEstablishedFuture = null;
    }

    @GuardedBy("mLock")
    protected void notifySessionEstablishmentFailed(@NonNull String message) {
        assert mSessionEstablishedFuture != null;
        Log.w(TAG, "Session establishment failed: " + message);
        mSessionEstablishedFuture.completeExceptionally(new ConnectionFailedException(message));
        mSessionEstablishedFuture = null;
    }

    @Override
    public void start() {
        startAsync();
    }

    @Override
    public  NotifyingCompletableFuture<String> startAsync() {
        final NotifyingCompletableFuture<String> future;

        synchronized (mLock) {
            mActiveSessionId = null;
            future = startDeferredFuture();
        }

        return future;
    }

    @Override
    public abstract void close();

    public static class ConnectionFailedException extends RuntimeException {
        public ConnectionFailedException(@NonNull String message) {
            super(message);
        }
    }

    /*package*/ final MobileWalletAdapterServer.MethodHandlers mMethodHandlers =
            new MobileWalletAdapterServer.MethodHandlers() {
                @Override
                public void authorize(@NonNull MobileWalletAdapterServer.AuthorizeRequest request) {
                    // Clear the active auth token immediately upon beginning authorization; it will be
                    // set to valid only on successful completion
                    synchronized (mLock) {
                        mActiveAuthorization = null;
                    }

                    if (request.authToken != null) {
                        doReauthorize(request);
                        return;
                    }

                    final String chain = request.chain != null
                            ? request.chain : ProtocolContract.CHAIN_SOLANA_MAINNET;

                    final NotifyingCompletableFuture<AuthorizeRequest.Result> future = new NotifyingCompletableFuture<>();
                    future.notifyOnComplete(f -> mIoHandler.post(() -> { // Note: run in IO thread context
                        try {
                            final AuthorizeRequest.Result authorize = f.get(); // won't block

                            if (authorize != null) {
                                final String name = request.identityName != null ? request.identityName : "";
                                final Uri uri = request.identityUri != null ? request.identityUri : Uri.EMPTY;
                                final Uri relativeIconUri = request.iconUri != null ? request.iconUri : Uri.EMPTY;
                                final AuthRecord authRecord = mAuthRepository.issue(name, uri, relativeIconUri,
                                        authorize.accounts,chain, authorize.walletUriBase, authorize.scope);
                                Log.d(TAG, "Authorize request completed successfully; issued auth: " + authRecord);
                                synchronized (mLock) {
                                    mActiveAuthorization = authRecord;
                                }

                                final String authToken = mAuthRepository.toAuthToken(authRecord);
                                request.complete(new MobileWalletAdapterServer.AuthorizationResult(authToken,
                                        authorize.accounts, authorize.walletUriBase, mWalletIcon, authorize.signInResult));
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
                            future, request.identityName, request.identityUri, request.iconUri, chain,
                            request.features, request.addresses, request.signInPayload)));
                }

                private void doReauthorize(@NonNull MobileWalletAdapterServer.AuthorizeRequest request) {
                    assert request.authToken != null;
                    final AuthRecord authRecord = mAuthRepository.fromAuthToken(request.authToken);
                    if (authRecord == null) {
                        mIoHandler.post(() -> request.completeExceptionally(
                                new MobileWalletAdapterServer.AuthorizationNotValidException(
                                        "auth_token not valid for this request")));
                        return;
                    }

                    if (request.chain != null && !authRecord.chain.equals(request.chain)) {
                        mIoHandler.post(() -> request.completeExceptionally(
                                new MobileWalletAdapterServer.AuthorizationNotValidException(
                                        "requested chain not valid for specified auth_token")));
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
                                            authToken, authRecord.getAuthorizedAccounts(),
                                            authRecord.walletUriBase, mWalletIcon, null)));
                        } catch (ExecutionException e) {
                            final Throwable cause = e.getCause();
                            assert(cause instanceof Exception); // expected to always be an Exception
                            request.completeExceptionally((Exception)cause);
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Unexpected interruption while waiting for reauthorization", e);
                        } catch (CancellationException e) {
                            request.cancel(true);
                        }
                    }));

                    mIoHandler.post(() -> mCallbacks.onReauthorizeRequest(new ReauthorizeRequest(
                            future, request.identityName, request.identityUri, request.iconUri,
                            authRecord.chain, authRecord.scope)));
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

                    if (authRecord != null) {
                        mIoHandler.post(() -> mCallbacks.onDeauthorizedEvent(new DeauthorizedEvent(
                                request, authRecord.identity.getName(), authRecord.identity.getUri(),
                                authRecord.identity.getRelativeIconUri(), authRecord.chain,
                                authRecord.scope)));
                    } else {
                        // No auth token was found. Just complete successfully, to avoid disclosing whether
                        // the auth token was valid.
                        mIoHandler.post(() -> request.complete(null));
                    }
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
                            request, authRecord.identity.getName(), authRecord.identity.getUri(),
                            authRecord.identity.getRelativeIconUri(), authRecord.scope,
                            authRecord.getAuthorizedAccounts(), authRecord.chain)));
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
                                authRecord.identity.getName(), authRecord.identity.getUri(),
                                authRecord.identity.getRelativeIconUri(), authRecord.scope,
                                authRecord.getAuthorizedAccounts(), authRecord.chain);
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
                            new SignAndSendTransactionsRequest(request, authRecord.identity.getName(),
                                    authRecord.identity.getUri(), authRecord.identity.getRelativeIconUri(),
                                    authRecord.scope, authRecord.getAuthorizedAccounts(), authRecord.chain)));
                }
            };
}
