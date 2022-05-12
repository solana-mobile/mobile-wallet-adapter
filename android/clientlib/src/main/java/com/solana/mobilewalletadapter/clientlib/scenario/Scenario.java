/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.scenario;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient;

public abstract class Scenario {
    @Nullable
    protected final Callbacks mCallbacks;

    protected Scenario(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
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
