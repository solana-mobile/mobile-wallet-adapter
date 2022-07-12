package com.solana.mobilewalletadapter.clientlib;

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
import com.solana.mobilewalletadapter.clientlib.protocol.RxMobileWalletAdapterClient;
import com.solana.mobilewalletadapter.clientlib.scenario.RxLocalAssociationScenario;
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;

import java.util.function.Function;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class RxMobileWalletAdapter {

    @NonNull
    private final RxLocalAssociationScenario mRxLocalAssociationScenario;

    public RxMobileWalletAdapter(@IntRange(from = 0) int clientTimeoutMs) {
        this.mRxLocalAssociationScenario = new RxLocalAssociationScenario(clientTimeoutMs);
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

    private <T> Single<T> startExecuteAndClose(Function<RxMobileWalletAdapterClient, Single<T>> functionToExecute) {
        return mRxLocalAssociationScenario
                .start()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(functionToExecute::apply)
                .zipWith(
                        mRxLocalAssociationScenario.close().toSingle(() -> true),
                        ((authorizeResult, b) -> authorizeResult)
                );
    }
}
