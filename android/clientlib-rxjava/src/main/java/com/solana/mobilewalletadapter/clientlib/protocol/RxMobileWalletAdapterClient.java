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
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignAndSendTransactionFuture;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignAndSendTransactionResult;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignPayloadFuture;
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignPayloadResult;
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;

import io.reactivex.Completable;
import io.reactivex.Single;

public class RxMobileWalletAdapterClient {

    @NonNull
    private final MobileWalletAdapterClient mMobileWalletAdapterClient;

    public RxMobileWalletAdapterClient(@IntRange(from = 0) int clientTimeoutMs) {
        mMobileWalletAdapterClient = new MobileWalletAdapterClient(clientTimeoutMs);
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
    public Single<SignPayloadResult> signTransaction(@NonNull String authToken,
                                                     @NonNull @Size(min = 1) byte[][] transactions) {
        try {
            SignPayloadFuture signPayloadFuture = mMobileWalletAdapterClient.signTransaction(authToken, transactions);
            return Single.fromFuture(signPayloadFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignPayloadResult> signMessage(@NonNull String authToken,
                                                 @NonNull @Size(min = 1) byte[][] messages) {
        try {
            SignPayloadFuture signPayloadFuture = mMobileWalletAdapterClient.signMessage(authToken, messages);
            return Single.fromFuture(signPayloadFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignAndSendTransactionResult> signAndSendTransaction(@NonNull String authToken,
                                                                       @NonNull @Size(min = 1) byte[][] transactions,
                                                                       @NonNull CommitmentLevel commitmentLevel,
                                                                       @Nullable String cluster,
                                                                       boolean skipPreflight,
                                                                       @Nullable CommitmentLevel preflightCommitmentLevel) {
        try {
            SignAndSendTransactionFuture signAndSendTransactionFuture = mMobileWalletAdapterClient.signAndSendTransaction(
                    authToken, transactions, commitmentLevel, cluster, skipPreflight, preflightCommitmentLevel
            );
            return Single.fromFuture(signAndSendTransactionFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    @CheckResult
    @NonNull
    public Single<SignAndSendTransactionResult> signAndSendTransaction(@NonNull String authToken,
                                                                       @NonNull @Size(min = 1) byte[][] transactions,
                                                                       @NonNull CommitmentLevel commitmentLevel) {
        try {
            SignAndSendTransactionFuture signAndSendTransactionFuture = mMobileWalletAdapterClient.signAndSendTransaction(
                    authToken, transactions, commitmentLevel
            );
            return Single.fromFuture(signAndSendTransactionFuture);
        } catch (Exception e) {
            return Single.error(e);
        }
    }
}
