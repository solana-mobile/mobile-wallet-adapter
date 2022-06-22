/*
 * Copyright (c) 2022 Solana Mobile Inc.
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
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.solana.mobilewalletadapter.fakedapp.databinding.ActivityMainBinding
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
                    viewBinding.btnRequestAirdrop.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignTxnX1.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignTxnX3.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignTxnX20.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignMsgX1.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignMsgX3.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignMsgX20.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignAndSendTxnX1.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignAndSendTxnX3.isEnabled = uiState.hasAuthToken
                    viewBinding.btnSignAndSendTxnX20.isEnabled = uiState.hasAuthToken

                    if (uiState.messages.isNotEmpty()) {
                        val message = uiState.messages.first()
                        Snackbar.make(viewBinding.root, message, Snackbar.LENGTH_SHORT)
                            .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                                override fun onDismissed(
                                    transientBottomBar: Snackbar?,
                                    event: Int
                                ) {
                                    viewModel.messageShown()
                                }
                            }).show()
                    }
                }
            }
        }

        viewBinding.btnGetCapabilities.setOnClickListener {
            lifecycleScope.launch { viewModel.getCapabilities(intentSender) }
        }

        viewBinding.btnAuthorize.setOnClickListener {
            lifecycleScope.launch { viewModel.authorize(intentSender) }
        }

        viewBinding.btnReauthorize.setOnClickListener {
            lifecycleScope.launch { viewModel.reauthorize(intentSender) }
        }

        viewBinding.btnDeauthorize.setOnClickListener {
            lifecycleScope.launch { viewModel.deauthorize(intentSender) }
        }

        viewBinding.btnRequestAirdrop.setOnClickListener {
            lifecycleScope.launch {
                viewModel.requestAirdrop()
            }
        }

        viewBinding.btnSignTxnX1.setOnClickListener {
            lifecycleScope.launch { viewModel.signTransaction(intentSender, 1) }
        }

        viewBinding.btnSignTxnX3.setOnClickListener {
            lifecycleScope.launch { viewModel.signTransaction(intentSender, 3) }
        }

        viewBinding.btnSignTxnX20.setOnClickListener {
            lifecycleScope.launch { viewModel.signTransaction(intentSender, 20) }
        }

        viewBinding.btnAuthorizeSign.setOnClickListener {
            lifecycleScope.launch { viewModel.authorizeAndSignTransaction(intentSender) }
        }

        viewBinding.btnSignMsgX1.setOnClickListener {
            lifecycleScope.launch { viewModel.signMessage(intentSender, 1) }
        }

        viewBinding.btnSignMsgX3.setOnClickListener {
            lifecycleScope.launch { viewModel.signMessage(intentSender, 3) }
        }

        viewBinding.btnSignMsgX20.setOnClickListener {
            lifecycleScope.launch { viewModel.signMessage(intentSender, 20) }
        }

        viewBinding.btnSignAndSendTxnX1.setOnClickListener {
            lifecycleScope.launch { viewModel.signAndSendTransaction(intentSender, 1) }
        }

        viewBinding.btnSignAndSendTxnX3.setOnClickListener {
            lifecycleScope.launch { viewModel.signAndSendTransaction(intentSender, 3) }
        }

        viewBinding.btnSignAndSendTxnX20.setOnClickListener {
            lifecycleScope.launch { viewModel.signAndSendTransaction(intentSender, 20) }
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