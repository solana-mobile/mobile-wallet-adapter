/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Size;

public class MobileWalletAdapterConfig {
    public static final String LEGACY_TRANSACTION_VERSION = "legacy";

    public final boolean supportsSignAndSendTransactions;

    @IntRange(from = 0)
    public final int maxTransactionsPerSigningRequest;

    @IntRange(from = 0)
    public final int maxMessagesPerSigningRequest;

    // Verified to only contain LEGACY_TRANSACTION_VERSION and non-negative integers
    @NonNull
    @Size(min = 1)
    public final Object[] supportedTransactionVersions;

    public MobileWalletAdapterConfig(boolean supportsSignAndSendTransactions,
                                     @IntRange(from = 0) int maxTransactionsPerSigningRequest,
                                     @IntRange(from = 0) int maxMessagesPerSigningRequest,
                                     @NonNull @Size(min = 1) Object[] supportedTransactionVersions) {
        this.supportsSignAndSendTransactions = supportsSignAndSendTransactions;
        this.maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest;
        this.maxMessagesPerSigningRequest = maxMessagesPerSigningRequest;

        for (Object o : supportedTransactionVersions) {
            if (!((o instanceof String) && LEGACY_TRANSACTION_VERSION.equals((String)o)) &&
                    !((o instanceof Integer) && ((Integer)o >= 0))) {
                throw new IllegalArgumentException("supportedTransactionVersions must be either the string \"legacy\" or a non-negative integer");
            }
        }
        this.supportedTransactionVersions = supportedTransactionVersions;
    }
}
