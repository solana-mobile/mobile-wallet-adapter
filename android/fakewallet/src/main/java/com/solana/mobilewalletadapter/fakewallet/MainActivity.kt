/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.solana.mobilewalletadapter.fakewallet.databinding.ActivityMainBinding
import com.solana.mobilewalletadapter.fakewallet.ui.scanqr.ScanQRActivity

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Handle layout insets to avoid overlapping top and bottom system bars on Android 15+
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.root) { v, windowInsets ->
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

        viewBinding.buttonStartRemote.setOnClickListener {
            startActivity(Intent(applicationContext, ScanQRActivity::class.java))
        }
    }
}