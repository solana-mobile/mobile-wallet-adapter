package com.solana.mobilewalletadapter.common.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class MobileWalletAdapterService extends Service {

    private final IBinder mToken;

    public MobileWalletAdapterService() {
        mToken = new Binder();
    }

    @Override
    public @Nullable IBinder onBind(Intent intent) {
        return mToken;
    }
}
