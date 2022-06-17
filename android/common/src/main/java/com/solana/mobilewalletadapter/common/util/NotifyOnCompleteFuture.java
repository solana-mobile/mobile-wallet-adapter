/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common.util;

import androidx.annotation.NonNull;

import java.util.concurrent.Future;

public interface NotifyOnCompleteFuture<T> extends Future<T> {
    void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<T>> cb);

    interface OnCompleteCallback<T extends Future<?>> {
        void onComplete(@NonNull T future);
    }
}
