/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.transaction;

import androidx.annotation.NonNull;

public class TransactionVersion {
    public static final String LEGACY = "legacy";

    public static boolean supportsLegacy(@NonNull Object[] supportedTransactionVersions) {
        for (final Object o : supportedTransactionVersions) {
            if (!(o instanceof String)) continue;
            if (LEGACY.equals((String)o)) {
                return true;
            }
        }
        return false;
    }

    public static boolean supportsVersion(@NonNull Object[] supportedTransactionVersions, int version) {
        for (final Object o : supportedTransactionVersions) {
            if (!(o instanceof Integer)) continue;
            if ((Integer)o == version) {
                return true;
            }
        }
        return false;
    }

    // Utility class with static methods; not constructable
    private TransactionVersion() {
        throw new UnsupportedOperationException("not constructable");
    }
}
