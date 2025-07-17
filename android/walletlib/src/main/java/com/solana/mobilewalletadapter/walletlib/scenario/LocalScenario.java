/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.common.protocol.SessionProperties;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterSession;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class LocalScenario extends BaseScenario {
    private static final String TAG = LocalScenario.class.getSimpleName();

    private final PowerConfigProvider mPowerManager;

    @Nullable
    private ScheduledFuture<?> mNoConnectionTimeoutHandler;
    private final ScheduledExecutorService mTimeoutExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    protected LocalScenario(@NonNull Context context,
                            @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                            @NonNull AuthIssuerConfig authIssuerConfig,
                            @NonNull Callbacks callbacks,
                            @NonNull byte[] associationPublicKey) {
        this(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks,
                associationPublicKey, new DevicePowerConfigProvider(context), List.of());
    }

    /*package*/ LocalScenario(@NonNull Context context,
                              @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                              @NonNull AuthIssuerConfig authIssuerConfig,
                              @NonNull Callbacks callbacks,
                              @NonNull byte[] associationPublicKey,
                              @NonNull PowerConfigProvider powerConfigProvider,
                              @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions) {
        this(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey,
                powerConfigProvider, associationProtocolVersions, new DefaultWalletIconProvider(context));
    }

    /*package*/ LocalScenario(@NonNull Context context,
                              @NonNull MobileWalletAdapterConfig mobileWalletAdapterConfig,
                              @NonNull AuthIssuerConfig authIssuerConfig,
                              @NonNull Callbacks callbacks,
                              @NonNull byte[] associationPublicKey,
                              @NonNull PowerConfigProvider powerConfigProvider,
                              @NonNull List<SessionProperties.ProtocolVersion> associationProtocolVersions,
                              @NonNull WalletIconProvider iconProvider) {
        super(context, mobileWalletAdapterConfig, authIssuerConfig, callbacks, associationPublicKey,
                associationProtocolVersions, iconProvider);

        this.mPowerManager = powerConfigProvider;
    }

    @Override
    public NotifyingCompletableFuture<String> startAsync() {
        mIoHandler.post(this::startNoConnectionTimer);
        return super.startAsync();
    }

    @Override
    public MessageReceiver createMessageReceiver() {
        return new MobileWalletAdapterSession(
                this,
                new MobileWalletAdapterServer(mMobileWalletAdapterConfig, mIoLooper, mMethodHandlers),
                mSessionStateCallbacks);
    }

    private long getNoConnectionTimeout() {
        return mPowerManager.isLowPowerMode() ? mMobileWalletAdapterConfig.noConnectionWarningTimeoutMs : 0L;
    }

    private void startNoConnectionTimer() {
        // we cant actually check if a connection is still alive, so instead we start a timer
        // that assumes we do not have connection if the timeout is reached. Therefore, we
        // MUST cancel this timer if we receive any connections or messages before it ends
        long noConnectionTimeout = getNoConnectionTimeout();
        if (noConnectionTimeout > 0)
            mNoConnectionTimeoutHandler = mTimeoutExecutorService.schedule(() -> {
                Log.i(TAG, "No connection timeout reached");
                mIoHandler.post(((Callbacks) mCallbacks)::onLowPowerAndNoConnection);
            }, noConnectionTimeout, TimeUnit.MILLISECONDS);
    }

    private void stopNoConnectionTimer() {
        if (mNoConnectionTimeoutHandler != null) {
            mNoConnectionTimeoutHandler.cancel(true);
            mNoConnectionTimeoutHandler = null;
        }
    }

    private final MobileWalletAdapterSessionCommon.StateCallbacks mSessionStateCallbacks =
            new MobileWalletAdapterSessionCommon.StateCallbacks() {
                private final AtomicInteger mClientCount = new AtomicInteger();

                @Override
                public void onSessionEstablished() {
                    Log.d(TAG, "MobileWalletAdapter session established");
                    if (mClientCount.incrementAndGet() == 1) {
                        mIoHandler.post(LocalScenario.this::stopNoConnectionTimer);
                        synchronized (mLock) {
                            notifySessionEstablishmentSucceeded();
                        }
                        mIoHandler.post(mAuthRepository::start);
                        mIoHandler.post(mCallbacks::onScenarioServingClients);
                    }
                }

                @Override
                public void onSessionClosed() {
                    Log.d(TAG, "MobileWalletAdapter session terminated");
                    if (mClientCount.decrementAndGet() == 0) {
                        mIoHandler.post(LocalScenario.this::stopNoConnectionTimer);
                        synchronized (mLock) {
                            mActiveAuthorization = null;
                        }
                        mIoHandler.post(mCallbacks::onScenarioServingComplete);
                        mIoHandler.post(mAuthRepository::stop);
                    }
                }

                @Override
                public void onSessionError() {
                    Log.w(TAG, "MobileWalletAdapter session error");
                    mIoHandler.post(LocalScenario.this::stopNoConnectionTimer);
                    if (mClientCount.decrementAndGet() == 0) {
                        synchronized (mLock) {
                            mActiveAuthorization = null;
                        }
                        mIoHandler.post(mCallbacks::onScenarioServingComplete);
                        mIoHandler.post(mAuthRepository::stop);
                    }
                }
            };

    public interface Callbacks extends Scenario.Callbacks {
        void onLowPowerAndNoConnection();
    }
}