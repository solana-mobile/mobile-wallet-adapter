package com.solana.mobilewalletadapter.clientlib;

import android.annotation.SuppressLint;
import android.net.Uri;

import androidx.annotation.CheckResult;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizeResult;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.ReauthorizeResult;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignAndSendTransactionResult;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignPayloadResult;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.clientlib.protocol.RxMobileWalletAdapterClient;
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator;
import com.solana.mobilewalletadapter.clientlib.scenario.RxLocalAssociationScenario;
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class RxMobileWalletAdapter {

    @IntRange(from = 0)
    private final int mClientTimeoutMs;

    @NonNull
    private final RxLocalAssociationScenario mRxLocalAssociationScenario;

    @NonNull
    private final ActivityResultSender mActivityResultSender;

    @Nullable
    private final Uri mEndpointPrefix;

    /**
     * Allow only a single MWA connection at a time
     */
    @NonNull
    private Semaphore mSemaphore = new Semaphore(1);

    public RxMobileWalletAdapter(
            @IntRange(from = 0) int clientTimeoutMs,
            @NonNull ActivityResultSender activityResultSender,
            @Nullable Uri endpointPrefix
    ) {
        this.mClientTimeoutMs = clientTimeoutMs;
        this.mRxLocalAssociationScenario = new RxLocalAssociationScenario(clientTimeoutMs);
        this.mActivityResultSender = activityResultSender;
        this.mEndpointPrefix = endpointPrefix;
    }

    public int getPort() {
        return mRxLocalAssociationScenario.getPort();
    }

    @Nullable
    public MobileWalletAdapterSession getSession() {
        return mRxLocalAssociationScenario.getSession();
    }

    @CheckResult
    @NonNull
    public Single<AuthorizeResult> authorize(@Nullable Uri identityUri,
                                             @Nullable Uri iconUri,
                                             @Nullable String identityName) {
        return startExecuteAndClose(
                (client) -> client.authorize(identityUri, iconUri, identityName)
        );
    }

    @CheckResult
    @NonNull
    public Single<ReauthorizeResult> reauthorize(@Nullable Uri identityUri,
                                                 @Nullable Uri iconUri,
                                                 @Nullable String identityName,
                                                 @NonNull String authToken) {
        return startExecuteAndClose(
                (client) -> client.reauthorize(identityUri, iconUri, identityName, authToken)
        );
    }

    @CheckResult
    @NonNull
    public Completable deauthorize(@NonNull String authToken) {
        return startExecuteAndClose((client) -> client.deauthorize(authToken).toSingle(() -> true))
                .ignoreElement();
    }

    @CheckResult
    @NonNull
    public Single<SignPayloadResult> signTransaction(@NonNull String authToken,
                                                     @NonNull @Size(min = 1) byte[][] transactions) {
        return startExecuteAndClose(
                (client) -> client.signTransaction(authToken, transactions)
        );
    }

    @CheckResult
    @NonNull
    public Single<SignPayloadResult> signMessage(@NonNull String authToken,
                                                 @NonNull @Size(min = 1) byte[][] messages) {
        return startExecuteAndClose(
                (client) -> client.signMessage(authToken, messages)
        );
    }

    @CheckResult
    @NonNull
    public Single<SignAndSendTransactionResult> signAndSendTransaction(@NonNull String authToken,
                                                                       @NonNull @Size(min = 1) byte[][] transactions,
                                                                       @NonNull CommitmentLevel commitmentLevel,
                                                                       @Nullable String cluster,
                                                                       boolean skipPreflight,
                                                                       @Nullable CommitmentLevel preflightCommitmentLevel) {
        return startExecuteAndClose(
                (client) -> client.signAndSendTransaction(
                        authToken, transactions, commitmentLevel, cluster, skipPreflight, preflightCommitmentLevel
                )
        );
    }

    @CheckResult
    @NonNull
    public Single<SignAndSendTransactionResult> signAndSendTransaction(@NonNull String authToken,
                                                                       @NonNull @Size(min = 1) byte[][] transactions,
                                                                       @NonNull CommitmentLevel commitmentLevel) {
        return startExecuteAndClose(
                (client) -> client.signAndSendTransaction(authToken, transactions, commitmentLevel)
        );
    }

    @SuppressLint("CheckResult")
    private synchronized <T> Single<T> startExecuteAndClose(Function<RxMobileWalletAdapterClient, Single<T>> functionToExecute) {
        try {
            mSemaphore.acquire();

            // Launch the Association intent
            mActivityResultSender.launch(
                    LocalAssociationIntentCreator.createAssociationIntent(
                            mEndpointPrefix, getPort(), getSession()
                    )
            );

            // Return the chain of [Single] (start->execute->close)
            return mRxLocalAssociationScenario
                    .start()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap(functionToExecute::apply)
                    .doFinally(() -> {
                                mRxLocalAssociationScenario.close().blockingAwait(mClientTimeoutMs, TimeUnit.MILLISECONDS);
                                mSemaphore.release();
                            }
                    );
        } catch (InterruptedException e) {
            return Single.error(e);
        }
    }
}
