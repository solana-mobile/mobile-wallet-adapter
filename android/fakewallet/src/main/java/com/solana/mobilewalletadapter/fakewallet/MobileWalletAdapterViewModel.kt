/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.fakewallet.usecase.ClientTrustUseCase
import com.solana.mobilewalletadapter.fakewallet.usecase.SolanaSigningUseCase
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.scenario.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.getAndUpdate
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.nio.charset.StandardCharsets

class MobileWalletAdapterViewModel(application: Application) : AndroidViewModel(application) {
    private val _mobileWalletAdapterServiceEvents =
        MutableStateFlow<MobileWalletAdapterServiceRequest>(MobileWalletAdapterServiceRequest.None)
    val mobileWalletAdapterServiceEvents =
        _mobileWalletAdapterServiceEvents.asSharedFlow() // expose as event stream, rather than a stateful object

    private var clientTrustUseCase: ClientTrustUseCase? = null
    private var scenario: Scenario? = null

    fun processLaunch(intent: Intent?, callingPackage: String?): Boolean {
        if (intent == null) {
            Log.e(TAG, "No Intent available")
            return false
        } else if (intent.data == null) {
            Log.e(TAG, "Intent has no data URI")
            return false
        }

        val associationUri = intent.data?.let { uri -> AssociationUri.parse(uri) }
        if (associationUri == null) {
            Log.e(TAG, "Unsupported association URI '${intent.data}'")
            return false
        } else if (associationUri !is LocalAssociationUri) {
            Log.w(TAG, "Current implementation of fakewallet does not support remote clients")
            return false
        }

        clientTrustUseCase = ClientTrustUseCase(
            viewModelScope,
            getApplication<Application>().packageManager,
            callingPackage,
            associationUri
        )

        scenario = associationUri.createScenario(
            getApplication<FakeWalletApplication>().applicationContext,
            MobileWalletAdapterConfig(true, 10, 10),
            AuthIssuerConfig("fakewallet"),
            MobileWalletAdapterScenarioCallbacks()
        ).also { it.start() }

        return true
    }

    override fun onCleared() {
        scenario?.close()
        scenario = null
    }

    fun authorizeDapp(
        request: MobileWalletAdapterServiceRequest.AuthorizeDapp,
        authorized: Boolean
    ) {
        if (rejectStaleRequest(request)) {
            return
        }

        viewModelScope.launch {
            if (authorized) {
                val keypair = getApplication<FakeWalletApplication>().keyRepository.generateKeypair()
                val publicKey = keypair.public as Ed25519PublicKeyParameters
                Log.d(TAG, "Generated a new keypair (pub=${publicKey.encoded.contentToString()}) for authorize request")
                request.request.completeWithAuthorize(
                    publicKey.encoded,
                    null,
                    request.sourceVerificationState.authorizationScope.encodeToByteArray()
                )
            } else {
                request.request.completeWithDecline()
            }
        }
    }

    fun signPayloadSimulateSign(request: MobileWalletAdapterServiceRequest.SignPayload) {
        if (rejectStaleRequest(request)) {
            return
        }

        viewModelScope.launch {
            val keypair = getApplication<FakeWalletApplication>().keyRepository.getKeypair(request.request.publicKey)
            check(keypair != null) { "Unknown public key for signing request" }

            val valid = BooleanArray(request.request.payloads.size) { true }
            val signedPayloads = when (request) {
                is MobileWalletAdapterServiceRequest.SignTransaction ->
                    Array(request.request.payloads.size) { i ->
                        try {
                            SolanaSigningUseCase.signTransaction(request.request.payloads[i], keypair).signedPayload
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "Transaction [$i] is not a valid Solana transaction", e)
                            valid[i] = false
                            byteArrayOf()
                        }
                    }
                is MobileWalletAdapterServiceRequest.SignMessage ->
                    Array(request.request.payloads.size) { i ->
                        SolanaSigningUseCase.signMessage(request.request.payloads[i], keypair).signedPayload
                    }
            }

            if (valid.all { it }) {
                Log.d(TAG, "Simulating signing with ${request.request.publicKey}")
                request.request.completeWithSignedPayloads(signedPayloads)
            } else {
                Log.e(TAG, "One or more transactions not valid")
                request.request.completeWithInvalidPayloads(valid)
            }
        }
    }

    fun signPayloadDeclined(request: MobileWalletAdapterServiceRequest.SignPayload) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithDecline()
    }

    fun signPayloadSimulateReauthorizationRequired(request: MobileWalletAdapterServiceRequest.SignPayload) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithReauthorizationRequired()
    }

    fun signPayloadSimulateAuthTokenInvalid(request: MobileWalletAdapterServiceRequest.SignPayload) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithAuthTokenNotValid()
    }

    fun signPayloadSimulateInvalidPayload(request: MobileWalletAdapterServiceRequest.SignPayload) {
        if (rejectStaleRequest(request)) {
            return
        }
        val valid = BooleanArray(request.request.payloads.size) { i -> i != 0 }
        request.request.completeWithInvalidPayloads(valid)
    }

    fun signPayloadSimulateTooManyPayloads(request: MobileWalletAdapterServiceRequest.SignPayload) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithTooManyPayloads()
    }

    fun signAndSendTransactionSimulateSign(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        viewModelScope.launch {
            val keypair = getApplication<FakeWalletApplication>().keyRepository.getKeypair(request.request.publicKey)
            check(keypair != null) { "Unknown public key for signing request" }

            val valid = BooleanArray(request.request.payloads.size) { true }
            val signatures = Array(request.request.payloads.size) { i ->
                try {
                    SolanaSigningUseCase.signTransaction(request.request.payloads[i], keypair).signature
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Transaction [$i] is not a valid Solana transaction", e)
                    valid[i] = false
                    byteArrayOf()
                }
            }

            if (valid.all { it }) {
                Log.d(TAG, "Simulating signing with ${request.request.publicKey}")
                if (!updateExistingRequest(request, request.copy(signatures = signatures))) {
                    return@launch
                }
            } else {
                Log.e(TAG, "One or more transactions not valid")
                if (rejectStaleRequest(request)) {
                    return@launch
                }
                request.request.completeWithInvalidSignatures(valid)
            }
        }
    }

    fun signAndSendTransactionDeclined(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithDecline()
    }

    fun signAndSendTransactionSimulateReauthorizationRequired(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithReauthorizationRequired()
    }

    fun signAndSendTransactionSimulateAuthTokenInvalid(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithAuthTokenNotValid()
    }

    fun signAndSendTransactionSimulateInvalidPayload(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }
        val valid = BooleanArray(request.request.payloads.size) { i -> i != 0 }
        request.request.completeWithInvalidSignatures(valid)
    }

    fun signAndSendTransactionCommitmentReached(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }

        Log.d(TAG, "Simulating commitmentLevel=${request.request.commitmentLevel} reached on cluster=${request.request.cluster}")

        request.request.completeWithSignatures(request.signatures!!)
    }

    fun signAndSendTransactionCommitmentNotReached(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }

        Log.d(TAG, "Simulating commitmentLevel=${request.request.commitmentLevel} NOT reached on cluster=${request.request.cluster}")

        val committed = BooleanArray(request.request.payloads.size) { i -> i != 0 }
        request.request.completeWithNotCommitted(request.signatures!!, committed)
    }

    fun signAndSendTransactionSimulateTooManyPayloads(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithTooManyPayloads()
    }

    private fun rejectStaleRequest(request: MobileWalletAdapterServiceRequest): Boolean {
        if (!_mobileWalletAdapterServiceEvents.compareAndSet(
                request,
                MobileWalletAdapterServiceRequest.None
            )
        ) {
            Log.w(TAG, "Discarding stale request")
            if (request is MobileWalletAdapterServiceRequest.MobileWalletAdapterRemoteRequest) {
                request.request.cancel()
            }
            return true
        }
        return false
    }

    private fun <T : MobileWalletAdapterServiceRequest.MobileWalletAdapterRemoteRequest> updateExistingRequest(
        request: T,
        updated: T
    ): Boolean {
        require(request.request === updated.request) { "When updating a request, the same underlying ScenarioRequest is expected" }
        if (!_mobileWalletAdapterServiceEvents.compareAndSet(request, updated)
        ) {
            Log.w(TAG, "Discarding stale request")
            request.request.cancel()
            return false
        }
        return true
    }

    private fun cancelAndReplaceRequest(request: MobileWalletAdapterServiceRequest) {
        val oldRequest = _mobileWalletAdapterServiceEvents.getAndUpdate { request }
        if (oldRequest is MobileWalletAdapterServiceRequest.MobileWalletAdapterRemoteRequest) {
            oldRequest.request.cancel()
        }
    }

    private inner class MobileWalletAdapterScenarioCallbacks : Scenario.Callbacks {
        override fun onScenarioReady() = Unit
        override fun onScenarioServingClients() = Unit
        override fun onScenarioServingComplete() {
            viewModelScope.launch(Dispatchers.Main) {
                scenario?.close()
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.None)
            }
        }
        override fun onScenarioComplete() = Unit
        override fun onScenarioError() = Unit
        override fun onScenarioTeardownComplete() {
            viewModelScope.launch {
                // No need to cancel any outstanding request; the scenario is torn down, and so
                // cancelling a request that originated from it isn't actionable
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SessionTerminated)
            }
        }

        override fun onAuthorizeRequest(request: AuthorizeRequest) {
            val clientTrustUseCase = clientTrustUseCase!! // should never be null if we get here

            val authorizeDappRequest = MobileWalletAdapterServiceRequest.AuthorizeDapp(
                request,
                clientTrustUseCase.verificationInProgress
            )
            cancelAndReplaceRequest(authorizeDappRequest)

            val verify = clientTrustUseCase.verifyAuthorizationSource(request.identityUri)
            viewModelScope.launch {
                val verificationState = withTimeoutOrNull(SOURCE_VERIFICATION_TIMEOUT_MS) {
                    verify.await()
                } ?: clientTrustUseCase.verificationTimedOut

                if (!updateExistingRequest(
                        authorizeDappRequest,
                        authorizeDappRequest.copy(sourceVerificationState = verificationState)
                    )
                ) {
                    return@launch
                }
            }
        }

        override fun onReauthorizeRequest(request: ReauthorizeRequest) {
            val reverify = clientTrustUseCase!!.verifyReauthorizationSource(
                String(request.authorizationScope, StandardCharsets.UTF_8),
                request.identityUri
            )
            viewModelScope.launch {
                val verificationState = withTimeoutOrNull(SOURCE_VERIFICATION_TIMEOUT_MS) {
                    reverify.await()
                }
                when (verificationState) {
                    is ClientTrustUseCase.VerificationInProgress -> throw IllegalStateException()
                    is ClientTrustUseCase.VerificationSucceeded -> {
                        Log.i(TAG, "Reauthorization source verification succeeded")
                        request.completeWithReauthorize()
                    }
                    is ClientTrustUseCase.NotVerifiable -> {
                        Log.i(TAG, "Reauthorization source not verifiable; approving")
                        request.completeWithReauthorize()
                    }
                    is ClientTrustUseCase.VerificationFailed -> {
                        Log.w(TAG, "Reauthorization source verification failed")
                        request.completeWithDecline()
                    }
                    null -> {
                        Log.w(TAG, "Timed out waiting for reauthorization source verification")
                        request.completeWithDecline()
                    }
                }
            }
        }

        override fun onSignTransactionRequest(request: SignTransactionRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignTransaction(request))
            } else {
                request.completeWithDecline()
            }
        }

        override fun onSignMessageRequest(request: SignMessageRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignMessage(request))
            } else {
                request.completeWithDecline()
            }
        }

        override fun onSignAndSendTransactionRequest(request: SignAndSendTransactionRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignAndSendTransaction(request))
            } else {
                request.completeWithDecline()
            }
        }

        private fun verifyPrivilegedMethodSource(request: VerifiableIdentityRequest): Boolean {
            return clientTrustUseCase!!.verifyPrivilegedMethodSource(
                String(request.authorizationScope, StandardCharsets.UTF_8),
                request.identityUri
            )
        }
    }

    sealed interface MobileWalletAdapterServiceRequest {
        object None : MobileWalletAdapterServiceRequest
        object SessionTerminated : MobileWalletAdapterServiceRequest

        sealed class MobileWalletAdapterRemoteRequest(open val request: ScenarioRequest) : MobileWalletAdapterServiceRequest
        data class AuthorizeDapp(
            override val request: AuthorizeRequest,
            val sourceVerificationState: ClientTrustUseCase.VerificationState
        ) : MobileWalletAdapterRemoteRequest(request)
        sealed class SignPayload(override val request: SignPayloadRequest) : MobileWalletAdapterRemoteRequest(request)
        data class SignTransaction(override val request: SignTransactionRequest) : SignPayload(request)
        data class SignMessage(override val request: SignMessageRequest) : SignPayload(request)
        data class SignAndSendTransaction(
            override val request: SignAndSendTransactionRequest,
            val signatures: Array<ByteArray>? = null
        ) : MobileWalletAdapterRemoteRequest(request)
    }

    companion object {
        private val TAG = MobileWalletAdapterViewModel::class.simpleName
        private const val SOURCE_VERIFICATION_TIMEOUT_MS = 3000L
    }
}