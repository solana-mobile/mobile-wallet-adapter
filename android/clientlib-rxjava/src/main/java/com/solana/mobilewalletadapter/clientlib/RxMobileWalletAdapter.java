package com.solana.mobilewalletadapter.clientlib;

import android.annotation.SuppressLint;
import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.clientlib.protocol.RxMobileWalletAdapterClient;
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator;
import com.solana.mobilewalletadapter.clientlib.scenario.RxLocalAssociationScenario;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class RxMobileWalletAdapter {

    @IntRange(from = 0)
    private final int mClientTimeoutMs;

    @Nullable
    private final Uri mEndpointPrefix;

    /**
     * Allow only a single MWA connection at a time
     */
    @NonNull
    private final Semaphore mSemaphore = new Semaphore(1);

    public RxMobileWalletAdapter(
            @IntRange(from = 0) int clientTimeoutMs,
            @Nullable Uri endpointPrefix
    ) {
        this.mClientTimeoutMs = clientTimeoutMs;
        this.mEndpointPrefix = endpointPrefix;
    }

    @SuppressLint("CheckResult")
    public Single<RxMobileWalletAdapterClient> transact(@NonNull ActivityResultSender activityResultSender) {
        try {
            mSemaphore.acquire();

            // Create and launch the Association intent
            RxLocalAssociationScenario rxLocalAssociationScenario = new RxLocalAssociationScenario(mClientTimeoutMs);
            activityResultSender.launch(
                    LocalAssociationIntentCreator.createAssociationIntent(
                            mEndpointPrefix, rxLocalAssociationScenario.getPort(), rxLocalAssociationScenario.getSession()
                    )
            );

            // Return the chain of [Single] (start->execute->close)
            return rxLocalAssociationScenario
                    .start()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .doFinally(() -> {
                        rxLocalAssociationScenario.close().blockingAwait(mClientTimeoutMs, TimeUnit.MILLISECONDS);
                                mSemaphore.release();
                            }
                    );
        } catch (InterruptedException e) {
            return Single.error(e);
        }
    }
}
