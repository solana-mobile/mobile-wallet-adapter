package com.solana.mobilewalletadapter.clientlib.protocol;

import android.net.Uri;

import androidx.annotation.CheckResult;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizeFuture;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizeResult;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.DeauthorizeFuture;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.ReauthorizeFuture;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.ReauthorizeResult;
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
    public Single<AuthorizeResult> authorize(@Nullable Uri identityUri,
                                             @Nullable Uri iconUri,
                                             @Nullable String identityName) {
        try {
            AuthorizeFuture authorizeFuture = mMobileWalletAdapterClient.authorize(identityUri, iconUri, identityName);
            return Single.fromFuture(authorizeFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<ReauthorizeResult> reauthorize(@Nullable Uri identityUri,
                                                 @Nullable Uri iconUri,
                                                 @Nullable String identityName,
                                                 @NonNull String authToken) {
        try {
            ReauthorizeFuture reauthorizeFuture = mMobileWalletAdapterClient.reauthorize(identityUri, iconUri, identityName, authToken);
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
    public Single<SignPayloadsResult> signTransactions(@NonNull String authToken,
                                                       @NonNull @Size(min = 1) byte[][] transactions) {
        try {
            SignPayloadsFuture signPayloadsFuture = mMobileWalletAdapterClient.signTransactions(authToken, transactions);
            return Single.fromFuture(signPayloadsFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignPayloadsResult> signMessages(@NonNull String authToken,
                                                   @NonNull @Size(min = 1) byte[][] messages) {
        try {
            SignPayloadsFuture signPayloadsFuture = mMobileWalletAdapterClient.signMessages(authToken, messages);
            return Single.fromFuture(signPayloadsFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignAndSendTransactionsResult> signAndSendTransactions(@NonNull String authToken,
                                                                         @NonNull @Size(min = 1) byte[][] transactions,
                                                                         @NonNull CommitmentLevel commitmentLevel,
                                                                         @Nullable String cluster,
                                                                         boolean skipPreflight,
                                                                         @Nullable CommitmentLevel preflightCommitmentLevel) {
        try {
            SignAndSendTransactionsFuture signAndSendTransactionsFuture = mMobileWalletAdapterClient.signAndSendTransactions(
                    authToken, transactions, commitmentLevel, cluster, skipPreflight, preflightCommitmentLevel
            );
            return Single.fromFuture(signAndSendTransactionsFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignAndSendTransactionsResult> signAndSendTransactions(@NonNull String authToken,
                                                                         @NonNull @Size(min = 1) byte[][] transactions,
                                                                         @NonNull CommitmentLevel commitmentLevel) {
        try {
            SignAndSendTransactionsFuture signAndSendTransactionsFuture = mMobileWalletAdapterClient.signAndSendTransactions(
                    authToken, transactions, commitmentLevel
            );
            return Single.fromFuture(signAndSendTransactionsFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }
}
