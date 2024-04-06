/*
 * Copyright (c) 2022 Solana Mobile Inc.
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
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest
import com.solana.mobilewalletadapter.fakewallet.R
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentSignPayloadBinding
import kotlinx.coroutines.launch

class SignPayloadFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentSignPayloadBinding

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
                        is MobileWalletAdapterServiceRequest.SignPayloads -> {
                            val res =
                                if (request is MobileWalletAdapterServiceRequest.SignTransactions) {
                                    R.string.label_sign_transactions
                                } else {
                                    R.string.label_sign_messages
                                }
                            viewBinding.textSignPayloads.setText(res)
                            viewBinding.textNumTransactions.text =
                                request.request.payloads.size.toString()

                            viewBinding.textAccount.text =
                                request.request.authorizedAccounts.filter { aa ->
                                    if (request is MobileWalletAdapterServiceRequest.SignMessages) {
                                        request.request.addresses.any { it contentEquals aa.publicKey }
                                    } else {
                                        true
                                    }
                                }.joinToString {
                                    it.accountLabel ?: it.displayAddress ?: Base58.encodeToString(it.publicKey)
                                }

                            viewBinding.btnAuthorize.setOnClickListener {
                                activityViewModel.signPayloadsSimulateSign(request)
                            }

                            viewBinding.btnDecline.setOnClickListener {
                                activityViewModel.signPayloadsDeclined(request)
                            }

                            viewBinding.btnSimulateAuthorizationFailed.setOnClickListener {
                                activityViewModel.signPayloadsSimulateAuthTokenInvalid(request)
                            }

                            viewBinding.btnSimulateInvalidPayloads.setOnClickListener {
                                activityViewModel.signPayloadsSimulateInvalidPayloads(request)
                            }

                            viewBinding.btnSimulateTooManyPayloads.setOnClickListener {
                                activityViewModel.signPayloadsSimulateTooManyPayloads(request)
                            }

                            viewBinding.btnSimulateInternalError.setOnClickListener {
                                activityViewModel.signPayloadsSimulateInternalError(request)
                            }
                        }
                        is MobileWalletAdapterServiceRequest.SignAndSendTransactions -> {
                            request.signatures?.run {
                                // When signatures are present, move on to sending the transaction
                                findNavController().navigate(SignPayloadFragmentDirections.actionSendTransaction())
                                return@collect
                            }

                            viewBinding.textSignPayloads.setText(R.string.label_sign_transactions)
                            viewBinding.textNumTransactions.text =
                                request.request.payloads.size.toString()

                            viewBinding.textAccount.text = request.request.authorizedAccounts.joinToString {
                                it.accountLabel ?: it.displayAddress ?: Base58.encodeToString(it.publicKey)
                            }

                            viewBinding.btnAuthorize.setOnClickListener {
                                activityViewModel.signAndSendTransactionsSimulateSign(request)
                            }

                            viewBinding.btnDecline.setOnClickListener {
                                activityViewModel.signAndSendTransactionsDeclined(request)
                            }

                            viewBinding.btnSimulateAuthorizationFailed.setOnClickListener {
                                activityViewModel.signAndSendTransactionsSimulateAuthTokenInvalid(request)
                            }

                            viewBinding.btnSimulateInvalidPayloads.setOnClickListener {
                                activityViewModel.signAndSendTransactionsSimulateInvalidPayloads(request)
                            }

                            viewBinding.btnSimulateTooManyPayloads.setOnClickListener {
                                activityViewModel.signAndSendTransactionsSimulateTooManyPayloads(request)
                            }

                            viewBinding.btnSimulateInternalError.setOnClickListener {
                                activityViewModel.signAndSendTransactionsSimulateInternalError(request)
                            }
                        }
                        else -> {
                            // If several events are emitted back-to-back (e.g. during session
                            // teardown), this fragment may not have had a chance to transition
                            // lifecycle states. Only navigate if we believe we are still here.
                            findNavController().let { nc ->
                                if (nc.currentDestination?.id == R.id.fragment_sign_payload) {
                                    nc.navigate(SignPayloadFragmentDirections.actionSignPayloadComplete())
                                }
                            }
                        }
                    }
                }
            }
        }


    }
}