/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterSession;
import com.solana.mobilewalletadapter.walletlib.util.LooperThread;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class Scenario {
    private static final String TAG = Scenario.class.getSimpleName();

    @NonNull
    public final String associationToken;

    @NonNull
    protected final Looper mIoLooper;
    @NonNull
    protected final Handler mHandler;
    @NonNull
    protected final Callbacks mCallbacks;
    @NonNull
    private final MobileWalletAdapterServer.MethodHandlers mMethodHandlers;

    protected Scenario(@NonNull Callbacks callbacks,
                       @NonNull MobileWalletAdapterServer.MethodHandlers methodHandlers,
                       @NonNull String associationToken)
    {
        mCallbacks = callbacks;
        mMethodHandlers = methodHandlers;
        this.associationToken = associationToken;

        final LooperThread t = new LooperThread();
        t.start();
        mIoLooper = t.getLooper(); // blocks until Looper is available
        mHandler = new Handler(mIoLooper);
    }

    @Override
    protected void finalize() {
        mIoLooper.quitSafely();
    }

    public MessageReceiver createMessageReceiver() {
        return new MobileWalletAdapterSession(
                this,
                new MobileWalletAdapterServer(mIoLooper, mMethodHandlers),
                mSessionStateCallbacks,
                null, // use whatever the client specifies
                false);
    }

    public abstract void start();
    public abstract void close();

    private final MobileWalletAdapterSessionCommon.StateCallbacks mSessionStateCallbacks =
            new MobileWalletAdapterSessionCommon.StateCallbacks() {
        private final AtomicInteger mClientCount = new AtomicInteger();

        @Override
        public void onSessionEstablished() {
            Log.d(TAG, "MobileWalletAdapter session established");
            if (mClientCount.incrementAndGet() == 1) {
                mHandler.post(mCallbacks::onScenarioServingClients);
            }
        }

        @Override
        public void onSessionClosed() {
            Log.d(TAG, "MobileWalletAdapter session terminated");
            if (mClientCount.decrementAndGet() == 0) {
                mHandler.post(mCallbacks::onScenarioServingComplete);
            }
        }

        @Override
        public void onSessionError() {
            Log.w(TAG, "MobileWalletAdapter session error");
            if (mClientCount.decrementAndGet() == 0) {
                mHandler.post(mCallbacks::onScenarioServingComplete);
            }
        }
    };

    public interface Callbacks {
        void onScenarioReady();
        void onScenarioServingClients();
        void onScenarioServingComplete();
        void onScenarioComplete();
        void onScenarioError();
        void onScenarioTeardownComplete();
    }
}