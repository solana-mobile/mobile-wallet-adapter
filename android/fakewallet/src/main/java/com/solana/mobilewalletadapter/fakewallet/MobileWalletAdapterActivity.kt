/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest
import kotlinx.coroutines.launch

class MobileWalletAdapterActivity : AppCompatActivity() {
    private val viewModel: MobileWalletAdapterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_wallet_adapter)

        // Handle layout insets to avoid overlapping top and bottom system bars on Android 15+
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment)) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // Apply the insets as a margin to the view
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                topMargin = insets.top
                rightMargin = insets.right
                bottomMargin = insets.bottom
            }

            // Return CONSUMED so the window insets don't keep passing down to descendant views
            WindowInsetsCompat.CONSUMED
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    if (request is MobileWalletAdapterServiceRequest.SessionTerminated) {
                        finish()
                    } else if (request is MobileWalletAdapterServiceRequest.LowPowerNoConnection) {
                        // should use dialog fragment, etc. but this is a quick demo
                        AlertDialog.Builder(this@MobileWalletAdapterActivity)
                            .setTitle(R.string.label_low_power_mode_warning)
                            .setMessage(R.string.str_low_power_mode_warning_dsc)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                Log.w(TAG, "Connection failed due to device low power mode, returning to dapp.")
                                finish()
                            }
                            .show()
                    } else if (request is MobileWalletAdapterServiceRequest.SessionEstablishmentFailed) {
                        // should use dialog fragment, etc. but this is a quick demo
                        AlertDialog.Builder(this@MobileWalletAdapterActivity)
                            .setTitle(R.string.label_failed_session_establishment)
                            .setMessage(R.string.str_failed_session_establishment)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                Log.w(TAG, "Session could not be established, returning to dapp.")
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

    companion object {
        private val TAG = MobileWalletAdapterActivity::class.simpleName
    }
}