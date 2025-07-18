/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.ui.associate

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
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentAssociateBinding
import kotlinx.coroutines.launch

class AssociateFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentAssociateBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentAssociateBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                activityViewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    val navController = findNavController()
                    // If several events are emitted back-to-back (e.g. during session
                    // teardown), this fragment may not have had a chance to transition
                    // lifecycle states. Only navigate if we believe we are still here.
                    if (navController.currentDestination?.id != R.id.fragment_associate) {
                        return@collect
                    }

                    when (request) {
                        is MobileWalletAdapterServiceRequest.None ->
                            Unit
                        is MobileWalletAdapterServiceRequest.AuthorizeDapp ->
                            navController.navigate(AssociateFragmentDirections.actionAuthorizeDapp())
                        is MobileWalletAdapterServiceRequest.SignIn ->
                            navController.navigate(AssociateFragmentDirections.actionSignIn())
                        is MobileWalletAdapterServiceRequest.SignPayloads,
                        is MobileWalletAdapterServiceRequest.SignAndSendTransactions ->
                            navController.navigate(AssociateFragmentDirections.actionSignPayload())
                        is MobileWalletAdapterServiceRequest.SessionTerminated ->
                            Unit
                        is MobileWalletAdapterServiceRequest.LowPowerNoConnection ->
                            Unit
                        is MobileWalletAdapterServiceRequest.SessionEstablishmentFailed ->
                            Unit
                    }
                }
            }
        }

        viewBinding.btnEndSession.visibility =
            if (activityViewModel.isConnectionRemote()) View.VISIBLE else View.GONE

        viewBinding.btnEndSession.setOnClickListener {
            activityViewModel.endSession()
        }
    }
}