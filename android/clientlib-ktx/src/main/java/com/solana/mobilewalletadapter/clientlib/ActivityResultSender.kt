package com.solana.mobilewalletadapter.clientlib

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.whenResumed
import kotlinx.coroutines.coroutineScope

class ActivityResultSender(
    private val rootActivity: ComponentActivity
) {
    private var callback: ((Int) -> Unit)? = null

    private val activityResultLauncher: ActivityResultLauncher<Intent> =
        rootActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onActivityComplete(it)
        }

    internal suspend fun startActivityForResult(
        intent: Intent,
        onActivityCompleteCallback: (Int) -> Unit,
    ) = coroutineScope {
        // A previous Intent may still be pending resolution (via the onActivityComplete method).
        // Wait for the Activity lifecycle to reach the RESUMED state, which guarantees that any
        // previous Activity results will have been received and their callback cleared. Blocking
        // here will lead to either (a) the Activity eventually reaching the RESUMED state, or
        // (b) the Activity terminating, destroying it's lifecycle-linked scope and cancelling this
        // Job.
        rootActivity.lifecycle.whenResumed { // NOTE: runs in Dispatchers.MAIN context
            check(callback == null) { "Received an activity start request while another is pending" }
            callback = onActivityCompleteCallback

            try {
                activityResultLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                callback = null
                throw e
            }
        }
    }

    private fun onActivityComplete(activityResult: ActivityResult) {
        callback?.let { it(activityResult.resultCode) }
        callback = null
    }
}