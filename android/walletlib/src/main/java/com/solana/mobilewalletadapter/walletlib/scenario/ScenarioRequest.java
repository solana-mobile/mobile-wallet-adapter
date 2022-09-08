/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;

public interface ScenarioRequest {
    void cancel();
    void completeWithInternalError(@NonNull Exception e);
}
