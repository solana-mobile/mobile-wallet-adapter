/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.common.util;

import android.os.Handler;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NotifyingCompletableFuture<T> implements NotifyOnCompleteFuture<T> {
    private final Handler mHandler;

    private boolean mIsCancelled;
    private boolean mIsComplete;
    private T mResult;
    private Exception mException;
    private OnCompleteCallback<? super NotifyOnCompleteFuture<T>> mOnCompleteCallback;

    public NotifyingCompletableFuture(@NonNull Handler handler) {
        mHandler = handler;
    }

    public boolean complete(@Nullable T result) {
        synchronized (this) {
            if (mIsComplete) {
                return false;
            }
            mIsComplete = true;
            mResult = result;
            notifyAll();
            dispatchOnCompletionNotification();
        }

        return true;
    }

    public boolean completeExceptionally(@NonNull Exception ex) {
        synchronized (this) {
            if (mIsComplete) {
                return false;
            }
            mIsComplete = true;
            mException = ex;
            notifyAll();
            dispatchOnCompletionNotification();
        }

        return true;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (this) {
            if (mIsComplete) {
                return false;
            }
            mIsComplete = true;
            mIsCancelled = true;
            mException = new CancellationException();
            notifyAll();
            dispatchOnCompletionNotification();
        }

        return true;
    }

    @Override
    public boolean isCancelled() {
        synchronized (this) {
            return mIsCancelled;
        }
    }

    @Override
    public boolean isDone() {
        synchronized (this) {
            return mIsComplete;
        }
    }

    @Nullable
    @Override
    public T get() throws ExecutionException, InterruptedException {
        synchronized (this) {
            while (!mIsComplete) {
                wait();
            }
            if (mException != null) {
                throw new ExecutionException(mException);
            }
            return mResult;
        }
    }

    @Nullable
    @Override
    public T get(long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        if (unit == null) {
            throw new IllegalArgumentException("Invalid time unit specified");
        }

        synchronized (this) {
            final long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (!mIsComplete) {
                final long now = System.nanoTime();
                if (now >= deadline) break;
                unit.timedWait(this, deadline - now);
            }

            if (!mIsComplete) {
                throw new TimeoutException();
            } else if (mException != null) {
                throw new ExecutionException(mException);
            }
            return mResult;
        }
    }

    @Override
    public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<T>> cb) {
        final boolean completeImmediately;
        synchronized (this) {
            if (mOnCompleteCallback != null) {
                throw new UnsupportedOperationException("Only a single completion callback may be registered");
            }

            if (mIsComplete) {
                completeImmediately = true;
            } else {
                completeImmediately = false;
                mOnCompleteCallback = cb;
            }
        }

        if (completeImmediately) {
            mHandler.post(() -> cb.onComplete(this));
        }
    }

    @GuardedBy("this")
    private void dispatchOnCompletionNotification() {
        if (mOnCompleteCallback != null) {
            final OnCompleteCallback<? super NotifyOnCompleteFuture<T>> cb = mOnCompleteCallback;
            mOnCompleteCallback = null;
            mHandler.post(() -> cb.onComplete(this));
        }
    }
}
