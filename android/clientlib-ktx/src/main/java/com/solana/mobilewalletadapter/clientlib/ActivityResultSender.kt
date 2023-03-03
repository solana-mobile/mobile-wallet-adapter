package com.solana.mobilewalletadapter.clientlib

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.GuardedBy

class ActivityResultSender(
    rootActivity: ComponentActivity
) {
    @GuardedBy("this")
    private var callback: (() -> Unit)? = null

    private val activityResultLauncher: ActivityResultLauncher<Intent> =
        rootActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onActivityComplete()
        }

    fun startActivityForResult(intent: Intent, onActivityCompleteCallback: () -> Unit) {
        synchronized(this) {
            check(callback == null) { "Received an activity start request while another is pending" }
            callback = onActivityCompleteCallback
        }

        try {
            activityResultLauncher.launch(intent)
        } catch (exception: ActivityNotFoundException) {
            callback = null
            throw exception
        }
    }

    private fun onActivityComplete() {
        synchronized(this) {
            callback?.let { it() }
            callback = null
        }
    }
}