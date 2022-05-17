/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.ui.authorizedapp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentAuthorizeDappBinding
import kotlinx.coroutines.launch

class AuthorizeDappFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentAuthorizeDappBinding

    private var request: MobileWalletAdapterServiceRequest.AuthorizeDapp? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentAuthorizeDappBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                activityViewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    when (request) {
                        is MobileWalletAdapterServiceRequest.AuthorizeDapp -> {
                            this@AuthorizeDappFragment.request = request

                            if (request.request.identityUri?.isAbsolute == true &&
                                request.request.iconUri?.isHierarchical == true
                            ) {
                                val uri = Uri.withAppendedPath(
                                    request.request.identityUri!!,
                                    request.request.iconUri!!.encodedPath
                                )
                                Glide.with(this@AuthorizeDappFragment).load(uri)
                                    .into(viewBinding.imageIcon)
                            }
                            viewBinding.textName.text = request.request.identityName ?: "<no name>"
                            viewBinding.textUri.text = request.request.identityUri?.toString() ?: "<no URI>"
                        }
                        else -> {
                            this@AuthorizeDappFragment.request = null
                            findNavController().navigate(AuthorizeDappFragmentDirections.actionAuthorizeDappComplete())
                        }
                    }
                }
            }
        }

        viewBinding.btnAuthorize.setOnClickListener {
            request?.let {
                Log.i(TAG, "Authorizing dapp")
                activityViewModel.authorizeDapp(it, true)
            }
        }

        viewBinding.btnDecline.setOnClickListener {
            request?.let {
                Log.w(TAG, "Not authorizing dapp")
                activityViewModel.authorizeDapp(it, false)
            }
        }
    }

    companion object {
        private val TAG = AuthorizeDappFragment::class.simpleName
    }
}