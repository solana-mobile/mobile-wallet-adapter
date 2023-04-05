/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest
import kotlinx.coroutines.launch

class MobileWalletAdapterActivity : AppCompatActivity() {
    private val viewModel: MobileWalletAdapterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.slide_in_from_bottom, R.anim.slide_out_to_bottom)
        setContentView(R.layout.activity_mobile_wallet_adapter)

        val windowLayoutParams = window.attributes

        windowLayoutParams.gravity = Gravity.BOTTOM
        windowLayoutParams.flags = windowLayoutParams.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND

        window.attributes = windowLayoutParams

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    if (request is MobileWalletAdapterServiceRequest.SessionTerminated) {
                        finish()
                    } else if (request is MobileWalletAdapterServiceRequest.LowPowerNoConnection) {
                        // should use dialog fragment, etc. but this is a quick demo
                        AlertDialog.Builder(this@MobileWalletAdapterActivity)
                            .setTitle(R.string.low_power_mode_warning_title)
                            .setMessage(R.string.str_low_power_mode_warning_dsc)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                Log.w(TAG, "Connection failed due to device low power mode, returning to dapp.")
                                finish()
                            }
                            .show()
                    }
                }
            }
        }

        val result = viewModel.processLaunch(intent, callingPackage)
        if (!result) {
            Log.w(TAG, "Invalid launch intent; finishing activity")
            finish()
            return
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_from_bottom, R.anim.slide_out_to_bottom)
    }

    companion object {
        private val TAG = MobileWalletAdapterActivity::class.simpleName
    }
}