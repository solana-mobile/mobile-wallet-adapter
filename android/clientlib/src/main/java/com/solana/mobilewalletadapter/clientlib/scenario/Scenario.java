/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.scenario;

import androidx.annotation.IntRange;

import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;

public abstract class Scenario {
    public static final int DEFAULT_CLIENT_TIMEOUT_MS = 90000;

    @IntRange(from = 0)
    protected int mClientTimeoutMs;

    protected Scenario(@IntRange(from = 0) int clientTimeoutMs) {
        mClientTimeoutMs = clientTimeoutMs;
    }

    public abstract NotifyOnCompleteFuture<MobileWalletAdapterClient> start();
    public abstract NotifyOnCompleteFuture<Void> close();
}
