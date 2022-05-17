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
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentSendTransactionBinding
import kotlinx.coroutines.launch

class SendTransactionFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentSendTransactionBinding

    private var request: MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest.SignAndSendTransaction? = null

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
                        is MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest.SignAndSendTransaction -> {
                            this@SendTransactionFragment.request = request
                            viewBinding.textDesiredCommitment.text = request.request.commitmentLevel.toString()
                        }
                        else -> {
                            this@SendTransactionFragment.request = null
                            findNavController().navigate(SendTransactionFragmentDirections.actionSendTransactionComplete())
                        }
                    }
                }
            }
        }

        viewBinding.btnSimulateCommitmentReached.setOnClickListener {
            activityViewModel.signAndSendTransactionCommitmentReached(request!!)
        }

        viewBinding.btnSimulateCommitmentNotReached.setOnClickListener {
            activityViewModel.signAndSendTransactionCommitmentNotReached(request!!)
        }
    }
}