/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.solana.mobilewalletadapter.fakewallet.databinding.ActivityMainBinding
import com.solana.mobilewalletadapter.fakewallet.ui.scanqr.ScanQRActivity

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.buttonStartRemote.setOnClickListener {
            startActivity(Intent(applicationContext, ScanQRActivity::class.java))
        }
    }
}