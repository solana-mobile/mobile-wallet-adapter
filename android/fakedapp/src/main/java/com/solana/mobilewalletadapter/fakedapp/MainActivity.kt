/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.solana.mobilewalletadapter.fakedapp.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.btnAuthorize.setOnClickListener {
            lifecycleScope.launch {
                viewModel.authorize(intentSender)
            }
        }

        viewBinding.btnReauthorize.isEnabled = false
        viewBinding.btnDeauthorize.isEnabled = false
        viewBinding.btnSignX1.isEnabled = false
        viewBinding.btnSignX3.isEnabled = false
        viewBinding.btnAuthorizeSign.isEnabled = false
    }

    private val intentSender = object : MainViewModel.StartActivityForResultSender {
        override fun startActivityForResult(intent: Intent) {
            try {
                this@MainActivity.startActivityForResult(intent, 0)
            } catch (_: ActivityNotFoundException) {}
        }
    }
}