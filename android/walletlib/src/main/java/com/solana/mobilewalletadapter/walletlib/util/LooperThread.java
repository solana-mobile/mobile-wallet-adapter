/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.util;

import android.os.Looper;

public class LooperThread extends Thread {
    private Looper mLooper;

    public void run() {
        Looper.prepare();
        synchronized (this) {
            mLooper = Looper.myLooper();
            notifyAll();
        }
        Looper.loop();
    }

    public Looper getLooper() {
        synchronized (this) {
            while (mLooper == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted waiting for looper creation", e);
                }
            }
            return mLooper;
        }
    }
}