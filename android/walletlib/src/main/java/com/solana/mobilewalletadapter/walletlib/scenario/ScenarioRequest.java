/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;

import java.util.concurrent.Future;

public abstract class ScenarioRequest {
    @NonNull
    private final Future<?> mRequest;

    protected ScenarioRequest(@NonNull Future<?> request) {
        mRequest = request;
    }

    public void cancel() {
        mRequest.cancel(true);
    }
}
