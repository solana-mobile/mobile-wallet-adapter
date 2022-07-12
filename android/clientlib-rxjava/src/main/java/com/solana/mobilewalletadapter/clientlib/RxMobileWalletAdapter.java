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
import com.solana.mobilewalletadapter.clientlib.scenario.RxLocalAssociationScenario;
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;

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
        return mRxLocalAssociationScenario
                .start()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(rxMobileWalletAdapterClient ->
                        rxMobileWalletAdapterClient.authorize(identityUri, iconUri, identityName)
                )
                .zipWith(
                        mRxLocalAssociationScenario.close().toSingle(() -> true),
                        ((authorizeResult, b) -> authorizeResult)
                );
    }

    @CheckResult
    @NonNull
    public Single<ReauthorizeResult> reauthorize(@Nullable Uri identityUri,
                                                 @Nullable Uri iconUri,
                                                 @Nullable String identityName,
                                                 @NonNull String authToken) {
        return mRxLocalAssociationScenario
                .start()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(rxMobileWalletAdapterClient ->
                        rxMobileWalletAdapterClient.reauthorize(identityUri, iconUri, identityName, authToken)
                )
                .zipWith(
                        mRxLocalAssociationScenario.close().toSingle(() -> true),
                        ((authorizeResult, b) -> authorizeResult)
                );
    }

    @CheckResult
    @NonNull
    public Completable deauthorize(@NonNull String authToken) {
        return mRxLocalAssociationScenario
                .start()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapCompletable(rxMobileWalletAdapterClient ->
                        rxMobileWalletAdapterClient.deauthorize(authToken)
                )
                .andThen(mRxLocalAssociationScenario.close());
    }

    @CheckResult
    @NonNull
    public Single<SignPayloadResult> signTransaction(@NonNull String authToken,
                                                     @NonNull @Size(min = 1) byte[][] transactions) {
        return mRxLocalAssociationScenario
                .start()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(rxMobileWalletAdapterClient ->
                        rxMobileWalletAdapterClient.signTransaction(authToken, transactions)
                )
                .zipWith(
                        mRxLocalAssociationScenario.close().toSingle(() -> true),
                        ((authorizeResult, b) -> authorizeResult)
                );
    }

    @CheckResult
    @NonNull
    public Single<SignPayloadResult> signMessage(@NonNull String authToken,
                                                 @NonNull @Size(min = 1) byte[][] messages) {
        return mRxLocalAssociationScenario
                .start()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(rxMobileWalletAdapterClient ->
                        rxMobileWalletAdapterClient.signMessage(authToken, messages)
                )
                .zipWith(
                        mRxLocalAssociationScenario.close().toSingle(() -> true),
                        ((authorizeResult, b) -> authorizeResult)
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
        return mRxLocalAssociationScenario
                .start()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(rxMobileWalletAdapterClient ->
                        rxMobileWalletAdapterClient.signAndSendTransaction(
                                authToken, transactions, commitmentLevel, cluster, skipPreflight, preflightCommitmentLevel
                        )
                )
                .zipWith(
                        mRxLocalAssociationScenario.close().toSingle(() -> true),
                        ((authorizeResult, b) -> authorizeResult)
                );
    }

    @CheckResult
    @NonNull
    public Single<SignAndSendTransactionResult> signAndSendTransaction(@NonNull String authToken,
                                                                       @NonNull @Size(min = 1) byte[][] transactions,
                                                                       @NonNull CommitmentLevel commitmentLevel) {
        return mRxLocalAssociationScenario
                .start()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMap(rxMobileWalletAdapterClient ->
                        rxMobileWalletAdapterClient.signAndSendTransaction(
                                authToken, transactions, commitmentLevel
                        )
                )
                .zipWith(
                        mRxLocalAssociationScenario.close().toSingle(() -> true),
                        ((authorizeResult, b) -> authorizeResult)
                );
    }
}
