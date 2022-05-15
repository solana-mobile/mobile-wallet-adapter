/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.ui.signpayload

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
import com.solana.mobilewalletadapter.fakewallet.R
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentSignPayloadBinding
import kotlinx.coroutines.launch

class SignPayloadFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentSignPayloadBinding

    private var signPayload: MobileWalletAdapterServiceRequest.SignPayload? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentSignPayloadBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                activityViewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    when (request) {
                        is MobileWalletAdapterServiceRequest.SignPayload -> {
                            this@SignPayloadFragment.signPayload = request

                            val res =
                                if (request is MobileWalletAdapterServiceRequest.SignTransaction) {
                                    R.string.label_sign_transaction
                                } else {
                                    R.string.label_sign_message
                                }
                            viewBinding.textSignPayloads.setText(res)
                            viewBinding.textNumTransactions.text =
                                request.request.payloads.size.toString()
                        }
                        else -> {
                            this@SignPayloadFragment.signPayload = null
                            findNavController().navigate(SignPayloadFragmentDirections.actionSignPayloadComplete())
                        }
                    }
                }
            }
        }

        viewBinding.btnAuthorize.setOnClickListener {
            activityViewModel.signPayloadSimulateSign(signPayload!!)
        }

        viewBinding.btnDecline.setOnClickListener {
            activityViewModel.signPayloadDeclined(signPayload!!)
        }

        viewBinding.btnSimulateReauthorize.setOnClickListener {
            activityViewModel.signPayloadSimulateReauthorizationRequired(signPayload!!)
        }

        viewBinding.btnSimulateAuthorizationFailed.setOnClickListener {
            activityViewModel.signPayloadSimulateAuthTokenInvalid(signPayload!!)
        }

        viewBinding.btnSimulateInvalidPayload.setOnClickListener {
            activityViewModel.signPayloadSimulateInvalidPayload(signPayload!!)
        }
    }
}