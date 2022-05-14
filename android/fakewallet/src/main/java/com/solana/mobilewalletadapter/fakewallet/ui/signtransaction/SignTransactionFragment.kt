/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.ui.signtransaction

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
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentSignTransactionBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SignTransactionFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentSignTransactionBinding

    private var signTransaction: MobileWalletAdapterServiceRequest.SignTransaction? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentSignTransactionBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                activityViewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    when (request) {
                        is MobileWalletAdapterServiceRequest.SignTransaction -> {
                            this@SignTransactionFragment.signTransaction = request
                            viewBinding.textNumTransactions.text =
                                request.request.transactions.size.toString()
                        }
                        else -> {
                            this@SignTransactionFragment.signTransaction = null
                            findNavController().navigate(SignTransactionFragmentDirections.actionSignTransactionComplete())
                        }
                    }
                }
            }
        }

        viewBinding.btnAuthorize.setOnClickListener {
            activityViewModel.signTransactionSimulateSign(signTransaction!!)
        }

        viewBinding.btnDecline.setOnClickListener {
            activityViewModel.signTransactionDeclined(signTransaction!!)
        }

        viewBinding.btnSimulateReauthorize.setOnClickListener {
            activityViewModel.signTransactionSimulateReauthorizationRequired(signTransaction!!)
        }

        viewBinding.btnSimulateAuthorizationFailed.setOnClickListener {
            activityViewModel.signTransactionSimulateAuthTokenInvalid(signTransaction!!)
        }

        viewBinding.btnSimulateInvalidPayload.setOnClickListener {
            activityViewModel.signTransactionSimulateInvalidTransaction(signTransaction!!)
        }
    }
}