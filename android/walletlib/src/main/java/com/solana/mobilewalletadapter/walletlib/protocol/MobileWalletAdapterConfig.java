/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import androidx.annotation.IntRange;

public class MobileWalletAdapterConfig {
    public final boolean supportsSignAndSendTransaction;

    @IntRange(from = 0)
    public final int maxTransactionsPerSigningRequest;

    @IntRange(from = 0)
    public final int maxMessagesPerSigningRequest;

    public MobileWalletAdapterConfig(boolean supportsSignAndSendTransaction,
                                     @IntRange(from = 0) int maxTransactionsPerSigningRequest,
                                     @IntRange(from = 0) int maxMessagesPerSigningRequest) {
        this.supportsSignAndSendTransaction = supportsSignAndSendTransaction;
        this.maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest;
        this.maxMessagesPerSigningRequest = maxMessagesPerSigningRequest;
    }
}
