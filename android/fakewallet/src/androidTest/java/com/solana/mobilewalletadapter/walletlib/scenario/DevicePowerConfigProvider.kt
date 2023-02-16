package com.solana.mobilewalletadapter.walletlib.scenario

import android.content.Context

var TestScopeLowPowerMode: Boolean = false

/**
 * This class replaces [DevicePowerConfigProvider] in [com.solana.mobilewalletadapter.walletlib]
 *
 * This implementation allows any fakewallet instrumented tests to explicitly set the low power
 * state that will be reported by the power config provider. This allows us to test logic related
 * to low power mode without needing to manually inject a [PowerConfigProvider] mock into the app
 */
/*package*/ class DevicePowerConfigProvider(context: Context) :
    PowerConfigProvider {
    override fun isLowPowerMode(): Boolean {
        return TestScopeLowPowerMode
    }
}