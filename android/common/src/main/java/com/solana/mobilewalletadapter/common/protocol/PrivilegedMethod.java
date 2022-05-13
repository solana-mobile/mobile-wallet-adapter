/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.common.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.ProtocolContract;

public enum PrivilegedMethod {
    SignTransaction(ProtocolContract.METHOD_SIGN_TRANSACTION),
    SignAndSendTransaction(ProtocolContract.METHOD_SIGN_AND_SEND_TRANSACTION),
    SignMessage(ProtocolContract.METHOD_SIGN_MESSAGE);

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