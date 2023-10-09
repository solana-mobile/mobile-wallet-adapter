/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.util.Identifier;

public class MobileWalletAdapterConfig {
    public static final String LEGACY_TRANSACTION_VERSION = "legacy";

    @Deprecated
    public final boolean supportsSignAndSendTransactions;

    @IntRange(from = 0)
    public final long noConnectionWarningTimeoutMs;

    @IntRange(from = 0)
    public final int maxTransactionsPerSigningRequest;

    @IntRange(from = 0)
    public final int maxMessagesPerSigningRequest;

    // Verified to only contain LEGACY_TRANSACTION_VERSION and non-negative integers
    @NonNull
    @Size(min = 1)
    public final Object[] supportedTransactionVersions;

    @NonNull
    public final String[] optionalFeatures;

    @Deprecated
    public MobileWalletAdapterConfig(boolean supportsSignAndSendTransactions,
                                     @IntRange(from = 0) int maxTransactionsPerSigningRequest,
                                     @IntRange(from = 0) int maxMessagesPerSigningRequest,
                                     @NonNull @Size(min = 1) Object[] supportedTransactionVersions,
                                     @IntRange(from = 0) long noConnectionWarningTimeoutMs) {
        this.supportsSignAndSendTransactions = supportsSignAndSendTransactions;
        this.maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest;
        this.maxMessagesPerSigningRequest = maxMessagesPerSigningRequest;
        this.noConnectionWarningTimeoutMs = noConnectionWarningTimeoutMs;
        this.optionalFeatures = supportsSignAndSendTransactions
                ? new String[] { ProtocolContract.FEATURE_ID_SIGN_AND_SEND_TRANSACTIONS } : new String[] {};

        for (Object o : supportedTransactionVersions) {
            if (!((o instanceof String) && LEGACY_TRANSACTION_VERSION.equals((String)o)) &&
                    !((o instanceof Integer) && ((Integer)o >= 0))) {
                throw new IllegalArgumentException("supportedTransactionVersions must be either the string \"legacy\" or a non-negative integer");
            }
        }
        this.supportedTransactionVersions = supportedTransactionVersions;
    }

    public MobileWalletAdapterConfig(@IntRange(from = 0) int maxTransactionsPerSigningRequest,
                                     @IntRange(from = 0) int maxMessagesPerSigningRequest,
                                     @NonNull @Size(min = 1) Object[] supportedTransactionVersions,
                                     @IntRange(from = 0) long noConnectionWarningTimeoutMs,
                                     @NonNull String[] supportedFeatures) {
        this.maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest;
        this.maxMessagesPerSigningRequest = maxMessagesPerSigningRequest;
        this.noConnectionWarningTimeoutMs = noConnectionWarningTimeoutMs;

        for (Object o : supportedTransactionVersions) {
            if (!((o instanceof String) && LEGACY_TRANSACTION_VERSION.equals((String)o)) &&
                    !((o instanceof Integer) && ((Integer)o >= 0))) {
                throw new IllegalArgumentException("supportedTransactionVersions must be either the string \"legacy\" or a non-negative integer");
            }
        }
        this.supportedTransactionVersions = supportedTransactionVersions;

        boolean supportsSignAndSendTransactions = false;
        for (String featureId : supportedFeatures) {
            if (!Identifier.isValidIdentifier(featureId)) {
                throw new IllegalArgumentException("supportedFeatures must be a valid namespaced feature identifier of the form '{namespace}:{reference}'");
            }
            if (featureId.equals(ProtocolContract.FEATURE_ID_SIGN_AND_SEND_TRANSACTIONS)) {
                supportsSignAndSendTransactions = true;
                break;
            }
        }
        this.supportsSignAndSendTransactions = supportsSignAndSendTransactions;
        this.optionalFeatures = supportedFeatures;
    }
}
