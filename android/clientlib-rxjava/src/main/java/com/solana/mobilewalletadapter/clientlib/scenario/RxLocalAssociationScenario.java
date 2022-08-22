package com.solana.mobilewalletadapter.clientlib.scenario;

import androidx.annotation.CheckResult;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.clientlib.protocol.RxMobileWalletAdapterClient;

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
    public Single<RxMobileWalletAdapterClient> start() {
        return Single.fromFuture(mLocalAssociationScenario.start())
                .map(RxMobileWalletAdapterClient::new);
    }

    @CheckResult
    @NonNull
    public Completable close() {
        return Completable.fromFuture(mLocalAssociationScenario.close());
    }

    public int getPort() {
        return mLocalAssociationScenario.getPort();
    }

    @NonNull
    public MobileWalletAdapterSession getSession() {
        return mLocalAssociationScenario.getSession();
    }
}
