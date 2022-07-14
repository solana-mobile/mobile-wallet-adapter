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

    @Nullable
    private final Uri mEndpointPrefix;

    /**
     * Allow only a single MWA connection at a time
     */
    @NonNull
    private Semaphore mSemaphore = new Semaphore(1);

    public RxMobileWalletAdapter(
            @IntRange(from = 0) int clientTimeoutMs,
            @Nullable Uri endpointPrefix
    ) {
        this.mClientTimeoutMs = clientTimeoutMs;
        this.mRxLocalAssociationScenario = new RxLocalAssociationScenario(clientTimeoutMs);
        this.mEndpointPrefix = endpointPrefix;
    }

    @SuppressLint("CheckResult")
    public Single<RxMobileWalletAdapterClient> transact(@NonNull ActivityResultSender activityResultSender) {
        try {
            mSemaphore.acquire();

            // Launch the Association intent
            activityResultSender.launch(
                    LocalAssociationIntentCreator.createAssociationIntent(
                            mEndpointPrefix, mRxLocalAssociationScenario.getPort(), mRxLocalAssociationScenario.getSession()
                    )
            );

            // Return the chain of [Single] (start->execute->close)
            return mRxLocalAssociationScenario
                    .start()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
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
