package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.Nullable;

/*package*/ class DevicePowerConfigProvider implements PowerConfigProvider {

    @Nullable
    private PowerManager powerManager;

    public DevicePowerConfigProvider(Context context) {
        // should we throw an error if the this call returns null?
        this.powerManager = context.getSystemService(PowerManager.class);
    }

    @Override
    public boolean isLowPowerMode() {
        if (powerManager != null)
            return powerManager.isPowerSaveMode();
        else
            return false; // should we throw an error instead?
    }
}
