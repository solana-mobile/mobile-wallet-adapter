/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.ui.authorizedapp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterViewModel.MobileWalletAdapterServiceRequest
import com.solana.mobilewalletadapter.fakewallet.R
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentAuthorizeDappBinding
import kotlinx.coroutines.launch
import java.util.*

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
                                request.request.iconRelativeUri?.isHierarchical == true
                            ) {
                                val uri = Uri.withAppendedPath(
                                    request.request.identityUri!!,
                                    request.request.iconRelativeUri!!.encodedPath
                                )
                                viewBinding.imageIcon.loadImage(uri.toString())

                            }
                            viewBinding.textName.text = request.request.identityName ?: "<no name>"
                            viewBinding.textUri.text =
                                request.request.identityUri?.toString() ?: "<no URI>"
                        }
                        else -> {
                            this@AuthorizeDappFragment.request = null
                            // If several events are emitted back-to-back (e.g. during session
                            // teardown), this fragment may not have had a chance to transition
                            // lifecycle states. Only navigate if we believe we are still here.
                            findNavController().let { nc ->
                                if (nc.currentDestination?.id == R.id.fragment_authorize_dapp) {
                                    nc.navigate(AuthorizeDappFragmentDirections.actionAuthorizeDappComplete())
                                }
                            }
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

fun AppCompatImageView.loadImage(imgUrl: String?) {
    imgUrl?.let { url ->
        val imageLoader = if (url.lowercase(Locale.getDefault()).contains("svg")) {
            ImageLoader.Builder(this.context)
                .components {
                    add(SvgDecoder.Factory())
                }.build()
        } else {
            ImageLoader(context)
        }
        val request = ImageRequest.Builder(context).apply {
            data(url)
        }.target(this).build()

        imageLoader.enqueue(request)
    }
}