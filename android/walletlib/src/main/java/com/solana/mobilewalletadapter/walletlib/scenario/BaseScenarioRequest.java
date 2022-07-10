/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;

import java.util.concurrent.Future;

/*package*/ abstract class BaseScenarioRequest implements ScenarioRequest {
    @NonNull
    private final Future<?> mRequest;

    protected BaseScenarioRequest(@NonNull Future<?> request) {
        mRequest = request;
    }

    @Override
    public void cancel() {
        mRequest.cancel(true);
    }
}
