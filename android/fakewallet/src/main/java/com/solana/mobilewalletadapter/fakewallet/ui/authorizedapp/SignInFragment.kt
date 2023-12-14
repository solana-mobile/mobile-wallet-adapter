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
import android.widget.ImageView
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
import com.solana.mobilewalletadapter.fakewallet.databinding.FragmentSignInBinding
import com.solana.mobilewalletadapter.fakewallet.usecase.ClientTrustUseCase
import kotlinx.coroutines.launch

class SignInFragment : Fragment() {
    private val activityViewModel: MobileWalletAdapterViewModel by activityViewModels()
    private lateinit var viewBinding: FragmentSignInBinding

    private var request: MobileWalletAdapterServiceRequest.SignIn? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = FragmentSignInBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                activityViewModel.mobileWalletAdapterServiceEvents.collect { request ->
                    when (request) {
                        is MobileWalletAdapterServiceRequest.SignIn -> {
                            this@SignInFragment.request = request

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
                            viewBinding.textUri.text = request.request.identityUri?.toString() ?: "<no URI>"
                            viewBinding.textStatement.text = request.signInPayload.statement ?: "<no statement>"
                            viewBinding.textDomain.text = request.signInPayload.domain
                            viewBinding.textSiwsUri.text = request.signInPayload.uri.toString()
                            viewBinding.textIssuedAt.text = request.signInPayload.issuedAt
                            viewBinding.textVerificationState.setText(
                                when (request.sourceVerificationState) {
                                    is ClientTrustUseCase.VerificationInProgress -> R.string.str_verification_in_progress
                                    is ClientTrustUseCase.NotVerifiable -> R.string.str_verification_not_verifiable
                                    is ClientTrustUseCase.VerificationFailed -> R.string.str_verification_failed
                                    is ClientTrustUseCase.VerificationSucceeded -> R.string.str_verification_succeeded
                                }
                            )
                            viewBinding.textVerificationScope.text =
                                request.sourceVerificationState.authorizationScope
                        }
                        else -> {
                            this@SignInFragment.request = null
                            // If several events are emitted back-to-back (e.g. during session
                            // teardown), this fragment may not have had a chance to transition
                            // lifecycle states. Only navigate if we believe we are still here.
                            findNavController().let { nc ->
                                if (nc.currentDestination?.id == R.id.fragment_sign_in) {
                                    nc.navigate(SignInFragmentDirections.actionSignInComplete())
                                }
                            }
                        }
                    }
                }
            }
        }

        viewBinding.btnSignIn.setOnClickListener {
            request?.let {
                Log.i(TAG, "signing in")
                activityViewModel.signIn(it, true)
            }
        }

        viewBinding.btnDecline.setOnClickListener {
            request?.let {
                Log.w(TAG, "Rejecting sign in")
                activityViewModel.signIn(it, false)
            }
        }

        viewBinding.btnSimulateSignInNotSupported.setOnClickListener {
            request?.let {
                Log.w(TAG, "Simulating sign in not supported")
                activityViewModel.signInSimulateSignInNotSupported(it)
            }
        }

        viewBinding.btnSimulateInternalError.setOnClickListener {
            request?.let {
                Log.w(TAG, "Simulating internal error")
                activityViewModel.authorizationSimulateInternalError(it)
            }
        }
    }

    companion object {
        private val TAG = AuthorizeDappFragment::class.simpleName
    }
}

private fun ImageView.loadImage(imgUrl: String?) {
    imgUrl?.let { url ->
        val imageLoader =
            ImageLoader.Builder(context)
                .components {
                    add(SvgDecoder.Factory())
                }.build()
        val request = ImageRequest.Builder(context).apply {
            data(url)
        }.target(onSuccess = { drawable ->
            setImageDrawable(drawable)
        }).build()

        imageLoader.enqueue(request)
    }
}