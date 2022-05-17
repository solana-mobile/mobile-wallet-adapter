/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.scenario;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient;

public abstract class Scenario {
    public static final int DEFAULT_CLIENT_TIMEOUT_MS = 90000;

    @Nullable
    protected final Callbacks mCallbacks;

    @NonNull
    protected final MobileWalletAdapterClient mMobileWalletAdapterClient;

    protected Scenario(@IntRange(from = 0) int clientTimeoutMs,
                       @Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
        mMobileWalletAdapterClient = new MobileWalletAdapterClient(clientTimeoutMs);
    }

    public abstract void start();
    public abstract void close();

    public interface Callbacks {
        void onScenarioReady(@NonNull MobileWalletAdapterClient client);
        void onScenarioComplete();
        void onScenarioError();
        void onScenarioTeardownComplete();
    }
}
