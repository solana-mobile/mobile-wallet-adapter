/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.common.protocol;

import androidx.annotation.NonNull;

import java.io.IOException;

public interface MessageSender {
    void send(@NonNull byte[] message) throws IOException;
}
