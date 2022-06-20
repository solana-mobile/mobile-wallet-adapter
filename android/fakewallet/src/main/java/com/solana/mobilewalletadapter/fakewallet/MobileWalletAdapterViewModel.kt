/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.fakewallet.usecase.Base58EncodeUseCase
import com.solana.mobilewalletadapter.fakewallet.usecase.SolanaSigningUseCase
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.scenario.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.lang.RuntimeException

class MobileWalletAdapterViewModel(application: Application) : AndroidViewModel(application) {
    private val _mobileWalletAdapterServiceEvents =
        MutableStateFlow<MobileWalletAdapterServiceRequest>(MobileWalletAdapterServiceRequest.None)
    val mobileWalletAdapterServiceEvents =
        _mobileWalletAdapterServiceEvents.asSharedFlow() // expose as event stream, rather than a stateful object

    // TODO: do we need to track all of these?
    private var associationUri: AssociationUri? = null
    private var associationType: AssocationType? = null
    private var callingPackage: String? = null

    private var scenario: Scenario? = null

    fun processLaunch(intent: Intent?, callingPackage: String?): Boolean {
        if (intent == null) {
            Log.e(TAG, "No Intent available")
            return false
        } else if (intent.data == null) {
            Log.e(TAG, "Intent has no data URI")
            return false
        }

        val associationUri = AssociationUri.parse(intent.data!!) ?: return false

        val associationType = when (associationUri) {
            is LocalAssociationUri -> {
                if (intent.action == Intent.ACTION_VIEW && intent.hasCategory(Intent.CATEGORY_BROWSABLE)) {
                    if (callingPackage != null) {
                        AssocationType.LOCAL_FROM_APP
                    } else if (intent.hasExtra(Browser.EXTRA_APPLICATION_ID)) {
                        AssocationType.LOCAL_FROM_BROWSER
                    } else {
                        AssocationType.LOCAL_FROM_UNKNOWN
                    }
                } else {
                    AssocationType.LOCAL_FROM_UNKNOWN
                }
            }
            is RemoteAssociationUri -> AssocationType.REMOTE
            else ->
                throw RuntimeException("Unexpected association URI type")
        }

        this.associationUri = associationUri
        this.associationType = associationType
        this.callingPackage = callingPackage

        val scenario = associationUri.createScenario(
            getApplication<FakeWalletApplication>().applicationContext,
            MobileWalletAdapterConfig(true, 10, 10),
            AuthIssuerConfig("fakewallet"),
            MobileWalletAdapterScenarioCallbacks()
        )
        scenario.start()
        this.scenario = scenario

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
                val publicKeyBase58 = Base58EncodeUseCase.invoke(publicKey.encoded)
                Log.d(TAG, "Generated a new keypair (pub=$publicKeyBase58) for authorize request")
                request.request.completeWithAuthorize(publicKeyBase58, null)
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
                    SolanaSigningUseCase.signTransaction(request.request.payloads[i], keypair).signatureBase58
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Transaction [$i] is not a valid Solana transaction", e)
                    valid[i] = false
                    ""
                }
            }

            if (valid.all { it }) {
                Log.d(TAG, "Simulating signing with ${request.request.publicKey}")
                val requestWithSignatures = request.copy(signatures = signatures)
                if (rejectStaleRequest(request, requestWithSignatures)) {
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

        Log.d(TAG, "Simulating ${request.request.commitmentLevel} reached on RPC=${request.request.rpcEndpointUri}")

        request.request.completeWithSignatures(request.signatures!!)
    }

    fun signAndSendTransactionCommitmentNotReached(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }

        Log.d(TAG, "Simulating ${request.request.commitmentLevel} NOT reached on RPC ${request.request.rpcEndpointUri}")

        val committed = BooleanArray(request.request.payloads.size) { i -> i != 0 }
        request.request.completeWithNotCommitted(request.signatures!!, committed)
    }

    fun signAndSendTransactionSimulateTooManyPayloads(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithTooManyPayloads()
    }

    private fun rejectStaleRequest(
        request: MobileWalletAdapterServiceRequest,
        replacement: MobileWalletAdapterServiceRequest = MobileWalletAdapterServiceRequest.None
    ): Boolean {
        if (!_mobileWalletAdapterServiceEvents.compareAndSet(request, replacement)) {
            Log.w(TAG, "Discarding stale request")
            if (request is MobileWalletAdapterServiceRequest.MobileWalletAdapterRemoteRequest) {
                request.request.cancel()
            }
            return true
        }
        return false
    }

    private fun replaceCurrentRequest(request: MobileWalletAdapterServiceRequest) {
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
                replaceCurrentRequest(MobileWalletAdapterServiceRequest.None)
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
            viewModelScope.launch {
                replaceCurrentRequest(MobileWalletAdapterServiceRequest.AuthorizeDapp(request))
            }
        }

        override fun onSignTransactionRequest(request: SignTransactionRequest) {
            viewModelScope.launch {
                replaceCurrentRequest(MobileWalletAdapterServiceRequest.SignTransaction(request))
            }
        }

        override fun onSignMessageRequest(request: SignMessageRequest) {
            viewModelScope.launch {
                replaceCurrentRequest(MobileWalletAdapterServiceRequest.SignMessage(request))
            }
        }

        override fun onSignAndSendTransactionRequest(request: SignAndSendTransactionRequest) {
            viewModelScope.launch {
                replaceCurrentRequest(MobileWalletAdapterServiceRequest.SignAndSendTransaction(request))
            }
        }
    }

    enum class AssocationType {
        LOCAL_FROM_BROWSER, LOCAL_FROM_APP, LOCAL_FROM_UNKNOWN, REMOTE
    }

    sealed interface MobileWalletAdapterServiceRequest {
        object None : MobileWalletAdapterServiceRequest
        object SessionTerminated : MobileWalletAdapterServiceRequest

        sealed class MobileWalletAdapterRemoteRequest(open val request: ScenarioRequest) : MobileWalletAdapterServiceRequest
        data class AuthorizeDapp(override val request: AuthorizeRequest) : MobileWalletAdapterRemoteRequest(request)
        sealed class SignPayload(override val request: SignPayloadRequest) : MobileWalletAdapterRemoteRequest(request)
        class SignTransaction(request: SignTransactionRequest) : SignPayload(request)
        class SignMessage(request: SignMessageRequest) : SignPayload(request)
        data class SignAndSendTransaction(
            override val request: SignAndSendTransactionRequest,
            val signatures: Array<String>? = null
        ) : MobileWalletAdapterRemoteRequest(request)

    }

    companion object {
        private val TAG = MobileWalletAdapterViewModel::class.simpleName
    }
}