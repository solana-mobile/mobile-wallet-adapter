/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

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
                    uiState.hasAuthToken.let { isAuthorized ->
                        viewBinding.btnReauthorize.isEnabled = isAuthorized
                        viewBinding.btnDeauthorize.isEnabled = isAuthorized
                        viewBinding.btnRequestAirdrop.isEnabled = isAuthorized
                        viewBinding.btnSignTxnX1.isEnabled = isAuthorized
                        viewBinding.btnSignTxnX3.isEnabled = isAuthorized
                        viewBinding.btnSignTxnX20.isEnabled = isAuthorized
                        viewBinding.btnSignMsgX1.isEnabled = isAuthorized
                        viewBinding.btnSignMsgX3.isEnabled = isAuthorized
                        viewBinding.btnSignMsgX20.isEnabled = isAuthorized
                        viewBinding.btnSignAndSendTxnX1.isEnabled = isAuthorized
                        viewBinding.btnSignAndSendTxnX3.isEnabled = isAuthorized
                        viewBinding.btnSignAndSendTxnX20.isEnabled = isAuthorized
                    }

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
            viewModel.getCapabilities(intentSender)
        }

        viewBinding.btnAuthorize.setOnClickListener {
            viewModel.authorize(intentSender)
        }

        viewBinding.btnReauthorize.setOnClickListener {
            viewModel.reauthorize(intentSender)
        }

        viewBinding.btnDeauthorize.setOnClickListener {
            viewModel.deauthorize(intentSender)
        }

        viewBinding.btnRequestAirdrop.setOnClickListener {
            viewModel.requestAirdrop()
        }

        viewBinding.btnSignTxnX1.setOnClickListener {
            viewModel.signTransaction(intentSender, 1)
        }

        viewBinding.btnSignTxnX3.setOnClickListener {
            viewModel.signTransaction(intentSender, 3)
        }

        viewBinding.btnSignTxnX20.setOnClickListener {
            viewModel.signTransaction(intentSender, 20)
        }

        viewBinding.btnAuthorizeSign.setOnClickListener {
            viewModel.authorizeAndSignTransaction(intentSender)
        }

        viewBinding.btnSignMsgX1.setOnClickListener {
            viewModel.signMessage(intentSender, 1)
        }

        viewBinding.btnSignMsgX3.setOnClickListener {
            viewModel.signMessage(intentSender, 3)
        }

        viewBinding.btnSignMsgX20.setOnClickListener {
            viewModel.signMessage(intentSender, 20)
        }

        viewBinding.btnSignAndSendTxnX1.setOnClickListener {
            viewModel.signAndSendTransaction(intentSender, 1)
        }

        viewBinding.btnSignAndSendTxnX3.setOnClickListener {
            viewModel.signAndSendTransaction(intentSender, 3)
        }

        viewBinding.btnSignAndSendTxnX20.setOnClickListener {
            viewModel.signAndSendTransaction(intentSender, 20)
        }
    }

    private val intentSender = object : MainViewModel.StartActivityForResultSender {
        override fun startActivityForResult(intent: Intent) {
            this@MainActivity.startActivityForResult(intent, 0)
        }
    }
}