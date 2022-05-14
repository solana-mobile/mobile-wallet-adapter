/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.solana.mobilewalletadapter.fakedapp.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.uiState.collect { uiState ->
                    viewBinding.btnReauthorize.isEnabled = uiState.hasAuthToken
                    viewBinding.btnDeauthorize.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignX1.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignX3.isEnabled = uiState.hasAuthToken
                }
            }
        }

        viewBinding.btnAuthorize.setOnClickListener {
            lifecycleScope.launch { viewModel.authorize(intentSender) }
        }

        viewBinding.btnReauthorize.setOnClickListener {
            TODO("Implement")
        }

        viewBinding.btnDeauthorize.setOnClickListener {
            TODO("Implement")
        }

        viewBinding.btnSignX1.setOnClickListener {
            lifecycleScope.launch { viewModel.signX1(intentSender) }
        }

        viewBinding.btnSignX3.setOnClickListener {
            lifecycleScope.launch { viewModel.signX3(intentSender) }
        }

        viewBinding.btnAuthorizeSign.setOnClickListener {
            lifecycleScope.launch { viewModel.authorizeAndSign(intentSender) }
        }
    }

    private val intentSender = object : MainViewModel.StartActivityForResultSender {
        override fun startActivityForResult(intent: Intent) {
            try {
                this@MainActivity.startActivityForResult(intent, 0)
            } catch (_: ActivityNotFoundException) {}
        }
    }
}