/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.common.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum PrivilegedMethod {
    SignTransaction("sign_transaction");

    @NonNull
    public final String methodName;

    PrivilegedMethod(@NonNull String methodName) {
        this.methodName = methodName;
    }

    @Nullable
    public static PrivilegedMethod fromMethodName(@NonNull String methodName) {
        for (PrivilegedMethod pm : values()) {
            if (methodName.equals(pm.methodName)) {
                return pm;
            }
        }
        return null;
    }
}