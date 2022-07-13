package com.solana.mobilewalletadapter.clientlib

import android.content.Intent

interface ActivityResultSender {
    fun launch(intent: Intent)
}