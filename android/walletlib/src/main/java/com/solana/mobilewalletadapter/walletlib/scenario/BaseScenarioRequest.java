/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

/*package*/ abstract class BaseScenarioRequest<T extends NotifyingCompletableFuture<?>>
        implements ScenarioRequest {
    @NonNull
    protected final T mRequest;

    protected BaseScenarioRequest(@NonNull T request) {
        mRequest = request;
    }

    @Override
    public void cancel() {
        mRequest.cancel(true);
    }

    @Override
    public void completeWithInternalError(@NonNull Exception e) {
        mRequest.completeExceptionally(e);
    }
}
