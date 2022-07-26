package com.solana.mobilewalletadapter.clientlib.protocol;

import android.net.Uri;

import androidx.annotation.CheckResult;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationFuture;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.DeauthorizeFuture;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignAndSendTransactionsFuture;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignAndSendTransactionsResult;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignPayloadsFuture;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignPayloadsResult;
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;

import io.reactivex.Completable;
import io.reactivex.Single;

public class RxMobileWalletAdapterClient {

    @NonNull
    private final MobileWalletAdapterClient mMobileWalletAdapterClient;

    public RxMobileWalletAdapterClient(@IntRange(from = 0) int clientTimeoutMs) {
        mMobileWalletAdapterClient = new MobileWalletAdapterClient(clientTimeoutMs);
    }

    public RxMobileWalletAdapterClient(@NonNull MobileWalletAdapterClient mobileWalletAdapterClient) {
        mMobileWalletAdapterClient = mobileWalletAdapterClient;
    }

    @CheckResult
    @NonNull
    public Single<AuthorizationResult> authorize(@Nullable Uri identityUri,
                                                 @Nullable Uri iconUri,
                                                 @Nullable String identityName,
                                                 @Nullable String cluster) {
        try {
            AuthorizationFuture authorizationFuture = mMobileWalletAdapterClient.authorize(identityUri, iconUri, identityName, cluster);
            return Single.fromFuture(authorizationFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<AuthorizationResult> reauthorize(@Nullable Uri identityUri,
                                                   @Nullable Uri iconUri,
                                                   @Nullable String identityName,
                                                   @NonNull String authToken) {
        try {
            AuthorizationFuture reauthorizeFuture = mMobileWalletAdapterClient.reauthorize(identityUri, iconUri, identityName, authToken);
            return Single.fromFuture(reauthorizeFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Completable deauthorize(@NonNull String authToken) {
        try {
            DeauthorizeFuture deauthorizeFuture = mMobileWalletAdapterClient.deauthorize(authToken);
            return Completable.fromFuture(deauthorizeFuture);
        } catch (Exception e) {
            return Completable.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignPayloadsResult> signTransactions(@NonNull @Size(min = 1) byte[][] transactions) {
        try {
            SignPayloadsFuture signPayloadsFuture = mMobileWalletAdapterClient.signTransactions(transactions);
            return Single.fromFuture(signPayloadsFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignPayloadsResult> signMessages(@NonNull @Size(min = 1) byte[][] messages) {
        try {
            SignPayloadsFuture signPayloadsFuture = mMobileWalletAdapterClient.signMessages(messages);
            return Single.fromFuture(signPayloadsFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignAndSendTransactionsResult> signAndSendTransactions(@NonNull @Size(min = 1) byte[][] transactions,
                                                                         @NonNull CommitmentLevel commitmentLevel,
                                                                         boolean skipPreflight,
                                                                         @Nullable CommitmentLevel preflightCommitmentLevel) {
        try {
            SignAndSendTransactionsFuture signAndSendTransactionsFuture = mMobileWalletAdapterClient.signAndSendTransactions(
                    transactions, commitmentLevel, skipPreflight, preflightCommitmentLevel
            );
            return Single.fromFuture(signAndSendTransactionsFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignAndSendTransactionsResult> signAndSendTransactions(@NonNull @Size(min = 1) byte[][] transactions,
                                                                         @NonNull CommitmentLevel commitmentLevel) {
        try {
            SignAndSendTransactionsFuture signAndSendTransactionsFuture = mMobileWalletAdapterClient.signAndSendTransactions(
                    transactions, commitmentLevel
            );
            return Single.fromFuture(signAndSendTransactionsFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }
}
