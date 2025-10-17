/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.solana.mobilewalletadapter.common.protocol.SessionProperties
import com.solana.mobilewalletadapter.fakedapp.databinding.ActivityMainBinding
import com.solana.mobilewalletadapter.fakedapp.usecase.MemoTransactionVersion
import com.solana.mobilewalletadapter.fakedapp.usecase.MobileWalletAdapterUseCase.StartMobileWalletAdapterActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var viewBinding: ActivityMainBinding
    private val mwaLauncher =
        registerForActivityResult(StartMobileWalletAdapterActivity(lifecycle)) {}

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

        val accountLabels = mutableListOf<String>()

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
                        viewBinding.cbHasAuthToken.isChecked = isAuthorized
                    }

                    viewModel.supportedTxnVersions.indexOf(uiState.txnVersion).let { spinnerPos ->
                        if (spinnerPos > 0) viewBinding.spinnerTxnVer.setSelection(spinnerPos)
                    }

                    if (accountLabels != uiState.accountLabels) {
                        accountLabels.clear()
                        accountLabels.addAll(uiState.accountLabels ?: listOf())
                        (viewBinding.spinnerAccounts.adapter as? BaseAdapter)?.notifyDataSetChanged()
                    }

                    viewBinding.tvWalletUriPrefix.text =
                        uiState.walletUriBase?.toString() ?: getString(R.string.string_no_wallet_uri_prefix)
                    viewBinding.tvSessionVersion.text =
                        getString(uiState.sessionProtocolVersion?.let {
                            when (it) {
                                SessionProperties.ProtocolVersion.LEGACY -> R.string.string_session_version_legacy
                                SessionProperties.ProtocolVersion.V1 -> R.string.string_session_version_v1
                            }
                        } ?: R.string.string_no_session_version)

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
            viewModel.getCapabilities(mwaLauncher)
        }

        viewBinding.btnAuthorize.setOnClickListener {
            viewModel.authorize(mwaLauncher)
        }

        viewBinding.btnSignIn.setOnClickListener {
            viewModel.signInWithSolana(mwaLauncher)
        }

        viewBinding.btnReauthorize.setOnClickListener {
            viewModel.reauthorize(mwaLauncher)
        }

        viewBinding.btnDeauthorize.setOnClickListener {
            viewModel.deauthorize(mwaLauncher)
        }

        viewBinding.btnRequestAirdrop.setOnClickListener {
            viewModel.requestAirdrop()
        }

        viewBinding.btnSignTxnX1.setOnClickListener {
            viewModel.signTransactions(mwaLauncher, 1)
        }

        viewBinding.btnSignTxnX3.setOnClickListener {
            viewModel.signTransactions(mwaLauncher, 3)
        }

        viewBinding.btnSignTxnX20.setOnClickListener {
            viewModel.signTransactions(mwaLauncher, 20)
        }

        viewBinding.btnAuthorizeSign.setOnClickListener {
            viewModel.authorizeAndSignTransactions(mwaLauncher)
        }

        viewBinding.btnAuthorizeSignMsgTxn.setOnClickListener {
            viewModel.authorizeAndSignMessageAndSignTransaction(mwaLauncher)
        }

        viewBinding.spinnerTxnVer.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item,
                // mapping from view model txn version to localized UI string
                viewModel.supportedTxnVersions.map { txnVersion ->
                    getString(when (txnVersion) {
                        MemoTransactionVersion.Legacy -> R.string.string_txn_version_legacy
                        MemoTransactionVersion.V0 -> R.string.string_txn_version_v0
                    })
                }
            )

        viewBinding.spinnerTxnVer.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, selected: View?,
                                            index: Int, id: Long) {
                    viewModel.setTransactionVersion(viewModel.supportedTxnVersions[index])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // nothing to do
                }
            }

        viewBinding.btnSignMsgX1.setOnClickListener {
            viewModel.signMessages(mwaLauncher, 1)
        }

        viewBinding.btnSignMsgX3.setOnClickListener {
            viewModel.signMessages(mwaLauncher, 3)
        }

        viewBinding.btnSignMsgX20.setOnClickListener {
            viewModel.signMessages(mwaLauncher, 20)
        }

        viewBinding.btnSignAndSendTxnX1.setOnClickListener {
            viewModel.signAndSendTransactions(mwaLauncher, 1)
        }

        viewBinding.btnSignAndSendTxnX3.setOnClickListener {
            viewModel.signAndSendTransactions(mwaLauncher, 3)
        }

        viewBinding.btnSignAndSendTxnX20.setOnClickListener {
            viewModel.signAndSendTransactions(mwaLauncher, 20)
        }

        viewBinding.spinnerAccounts.adapter = ArrayAdapter(this@MainActivity,
            android.R.layout.simple_spinner_item, accountLabels)
        viewBinding.spinnerAccounts.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, selected: View?,
                                            index: Int, id: Long) {
                    viewModel.setSelectedAccount(index)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkIsWalletEndpointAvailable()
    }
}