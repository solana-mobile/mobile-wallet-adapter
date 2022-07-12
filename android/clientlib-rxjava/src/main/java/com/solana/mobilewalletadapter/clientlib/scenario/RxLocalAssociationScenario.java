package com.solana.mobilewalletadapter.clientlib.scenario;

import androidx.annotation.CheckResult;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient;

import io.reactivex.Completable;
import io.reactivex.Single;

public class RxLocalAssociationScenario {

    @NonNull
    private final LocalAssociationScenario mLocalAssociationScenario;

    public RxLocalAssociationScenario(@IntRange(from = 0) int clientTimeoutMs) {
        this.mLocalAssociationScenario = new LocalAssociationScenario(clientTimeoutMs);
    }

    @CheckResult
    @NonNull
    public Single<MobileWalletAdapterClient> start() {
        return Single.fromFuture(mLocalAssociationScenario.start());
    }

    @CheckResult
    @NonNull
    public Completable close() {
        return Completable.fromFuture(mLocalAssociationScenario.close());
    }
}
