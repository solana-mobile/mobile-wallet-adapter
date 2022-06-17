/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NotifyingCompletableFuture<T> implements NotifyOnCompleteFuture<T> {
    private boolean mIsCancelled;
    private boolean mIsComplete;
    private T mResult;
    private Exception mException;
    private OnCompleteCallback<? super NotifyOnCompleteFuture<T>> mOnCompleteCallback;

    public boolean complete(@Nullable T result) {
        synchronized (this) {
            if (mIsComplete) {
                return false;
            }
            mIsComplete = true;
            mResult = result;
            notifyAll();
        }
        dispatchOnCompletionNotification();
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
        }
        dispatchOnCompletionNotification();
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
            notifyAll();
        }
        dispatchOnCompletionNotification();
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
    public T get() throws ExecutionException, CancellationException, InterruptedException {
        synchronized (this) {
            while (!mIsComplete) {
                wait();
            }
            if (mIsCancelled) {
                throw new CancellationException();
            } else if (mException != null) {
                throw new ExecutionException(mException);
            }
            return mResult;
        }
    }

    @Nullable
    @Override
    public T get(long timeout, TimeUnit unit)
            throws ExecutionException, CancellationException, InterruptedException, TimeoutException {
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
            } else if (mIsCancelled) {
                throw new CancellationException();
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
            cb.onComplete(this);
        }
    }

    private void dispatchOnCompletionNotification() {
        final OnCompleteCallback<? super NotifyOnCompleteFuture<T>> cb;
        synchronized (this) {
            cb = mOnCompleteCallback;
            if (cb == null) {
                return;
            }
            mOnCompleteCallback = null;
        }
        cb.onComplete(this);
    }

    @NonNull
    @Override
    public String toString() {
        final String s;
        if (mIsCancelled) {
            s = "NotifyingCompletableFuture{CANCELLED}";
        } else if (mIsComplete) {
            if (mResult != null) {
                s = "NotifyingCompletableFuture{COMPLETE, mResult=" + mResult + '}';
            } else {
                s = "NotifyingCompletableFuture{EXCEPTION, mException=" + mException + '}';
            }
        } else {
            s = "NotifyingCompletableFuture{NOT_COMPLETE}";
        }
        return s;
    }
}
