/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import androidx.annotation.IntRange;

public class MobileWalletAdapterConfig {
    public final boolean supportsSignAndSendTransactions;

    @IntRange(from = 0)
    public final int maxTransactionsPerSigningRequest;

    @IntRange(from = 0)
    public final int maxMessagesPerSigningRequest;

    public MobileWalletAdapterConfig(boolean supportsSignAndSendTransactions,
                                     @IntRange(from = 0) int maxTransactionsPerSigningRequest,
                                     @IntRange(from = 0) int maxMessagesPerSigningRequest) {
        this.supportsSignAndSendTransactions = supportsSignAndSendTransactions;
        this.maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest;
        this.maxMessagesPerSigningRequest = maxMessagesPerSigningRequest;
    }
}
