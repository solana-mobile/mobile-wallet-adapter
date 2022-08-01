/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.ui.sendtransaction

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel
import com.solana.mobilewalletadapter.fakewallet.R
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentSendTransactionBinding
import kotlinx.coroutines.launch

class SendTransactionFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentSendTransactionBinding

    private var request: MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest.SignAndSendTransactions? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentSendTransactionBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                activityViewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    when (request) {
                        is MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest.SignAndSendTransactions -> {
                            this@SendTransactionFragment.request = request
                            viewBinding.textCluster.text = request.request.cluster
                        }
                        else -> {
                            this@SendTransactionFragment.request = null
                            // If several events are emitted back-to-back (e.g. during session
                            // teardown), this fragment may not have had a chance to transition
                            // lifecycle states. Only navigate if we believe we are still here.
                            findNavController().let { nc ->
                                if (nc.currentDestination?.id == R.id.fragment_send_transaction) {
                                    nc.navigate(SendTransactionFragmentDirections.actionSendTransactionComplete())
                                }
                            }
                        }
                    }
                }
            }
        }

        viewBinding.btnSimulateTransactionsSubmitted.setOnClickListener {
            activityViewModel.signAndSendTransactionsSubmitted(request!!)
        }

        viewBinding.btnSimulateTransactionsNotSubmitted.setOnClickListener {
            activityViewModel.signAndSendTransactionsNotSubmitted(request!!)
        }

        viewBinding.btnSendTransactionToCluster.setOnClickListener {
            activityViewModel.signAndSendTransactionsSend(request!!)
        }
    }
}