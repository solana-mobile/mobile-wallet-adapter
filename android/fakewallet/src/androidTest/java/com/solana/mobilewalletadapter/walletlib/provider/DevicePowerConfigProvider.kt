package com.solana.mobilewalletadapter.walletlib.provider

import android.content.Context

var TestScopeLowPowerMode: Boolean = false

class DevicePowerConfigProvider(context: Context) : PowerConfigProvider {
    override fun isLowPowerMode(): Boolean {
        return TestScopeLowPowerMode
    }
}