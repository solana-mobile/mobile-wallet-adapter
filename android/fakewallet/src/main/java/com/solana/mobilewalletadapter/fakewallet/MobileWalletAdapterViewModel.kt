/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.fakewallet.usecase.ClientTrustUseCase
import com.solana.mobilewalletadapter.fakewallet.usecase.SendTransactionsUseCase
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
                    "fakewallet",
                    null,
                    request.sourceVerificationState.authorizationScope.encodeToByteArray()
                )
            } else {
                request.request.completeWithDecline()
            }
        }
    }

    fun signPayloadsSimulateSign(request: MobileWalletAdapterServiceRequest.SignPayloads) {
        if (rejectStaleRequest(request)) {
            return
        }

        viewModelScope.launch {
            val keypair = getApplication<FakeWalletApplication>().keyRepository.getKeypair(request.request.authorizedPublicKey)
            check(keypair != null) { "Unknown public key for signing request" }

            val valid = BooleanArray(request.request.payloads.size) { true }
            val signedPayloads = when (request) {
                is MobileWalletAdapterServiceRequest.SignTransactions ->
                    Array(request.request.payloads.size) { i ->
                        try {
                            SolanaSigningUseCase.signTransaction(request.request.payloads[i], keypair).signedPayload
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "Transaction [$i] is not a valid Solana transaction", e)
                            valid[i] = false
                            byteArrayOf()
                        }
                    }
                is MobileWalletAdapterServiceRequest.SignMessages ->
                    Array(request.request.payloads.size) { i ->
                        SolanaSigningUseCase.signMessage(request.request.payloads[i], keypair).signedPayload
                    }
            }

            if (valid.all { it }) {
                Log.d(TAG, "Simulating signing with ${request.request.authorizedPublicKey}")
                request.request.completeWithSignedPayloads(signedPayloads)
            } else {
                Log.e(TAG, "One or more transactions not valid")
                request.request.completeWithInvalidPayloads(valid)
            }
        }
    }

    fun signPayloadsDeclined(request: MobileWalletAdapterServiceRequest.SignPayloads) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithDecline()
    }

    fun signPayloadsSimulateAuthTokenInvalid(request: MobileWalletAdapterServiceRequest.SignPayloads) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithAuthorizationNotValid()
    }

    fun signPayloadsSimulateInvalidPayloads(request: MobileWalletAdapterServiceRequest.SignPayloads) {
        if (rejectStaleRequest(request)) {
            return
        }
        val valid = BooleanArray(request.request.payloads.size) { i -> i != 0 }
        request.request.completeWithInvalidPayloads(valid)
    }

    fun signPayloadsSimulateTooManyPayloads(request: MobileWalletAdapterServiceRequest.SignPayloads) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithTooManyPayloads()
    }

    fun signAndSendTransactionsSimulateSign(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        viewModelScope.launch {
            val keypair = getApplication<FakeWalletApplication>().keyRepository.getKeypair(request.request.publicKey)
            check(keypair != null) { "Unknown public key for signing request" }

            val signingResults = request.request.payloads.map { payload ->
                try {
                    SolanaSigningUseCase.signTransaction(payload, keypair)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "not a valid Solana transaction", e)
                    SolanaSigningUseCase.Result(byteArrayOf(), byteArrayOf())
                }
            }

            val valid = signingResults.map { result -> result.signature.isNotEmpty() }
            if (valid.all { it }) {
                Log.d(TAG, "Simulating signing with ${request.request.publicKey}")
                val signatures = signingResults.map { result -> result.signature }
                val signedTransactions = signingResults.map { result -> result.signedPayload }
                val requestWithSignatures = request.copy(
                    signatures = signatures.toTypedArray(),
                    signedTransactions = signedTransactions.toTypedArray()
                )
                if (!updateExistingRequest(request, requestWithSignatures)) {
                    return@launch
                }
            } else {
                Log.e(TAG, "One or more transactions not valid")
                if (rejectStaleRequest(request)) {
                    return@launch
                }
                request.request.completeWithInvalidSignatures(valid.toBooleanArray())
            }
        }
    }

    fun signAndSendTransactionsDeclined(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithDecline()
    }

    fun signAndSendTransactionsSimulateAuthTokenInvalid(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithAuthorizationNotValid()
    }

    fun signAndSendTransactionsSimulateInvalidPayloads(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        if (rejectStaleRequest(request)) {
            return
        }
        val valid = BooleanArray(request.request.payloads.size) { i -> i != 0 }
        request.request.completeWithInvalidSignatures(valid)
    }

    fun signAndSendTransactionsSubmitted(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        if (rejectStaleRequest(request)) {
            return
        }

        Log.d(TAG, "Simulating transactions submitted on cluster=${request.request.cluster}")

        request.request.completeWithSignatures(request.signatures!!)
    }

    fun signAndSendTransactionsNotSubmitted(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        if (rejectStaleRequest(request)) {
            return
        }

        Log.d(TAG, "Simulating transactions NOT submitted on cluster=${request.request.cluster}")

        val signatures = request.signatures!!
        val notSubmittedSignatures = Array(signatures.size) { i ->
            if (i != 0) signatures[i] else null
        }
        request.request.completeWithNotSubmitted(notSubmittedSignatures)
    }

    fun signAndSendTransactionsSend(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        if (rejectStaleRequest(request)) {
            return
        }

        Log.d(TAG, "Sending transactions to ${request.endpointUri}")

        viewModelScope.launch(Dispatchers.IO) {
            request.signedTransactions!!
            request.signatures!!

            try {
                SendTransactionsUseCase(
                    request.endpointUri,
                    request.signedTransactions,
                    request.request.minContextSlot
                )
                Log.d(TAG, "All transactions submitted via RPC")
                request.request.completeWithSignatures(request.signatures)
            } catch (e: SendTransactionsUseCase.InvalidTransactionsException) {
                Log.e(TAG, "Failed submitting transactions via RPC", e)
                request.request.completeWithInvalidSignatures(e.valid)
            }
        }
    }

    fun signAndSendTransactionsSimulateTooManyPayloads(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
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

    private fun clusterToRpcUri(cluster: String?): Uri {
        return when (cluster) {
            ProtocolContract.CLUSTER_MAINNET_BETA ->
                Uri.parse("https://api.mainnet-beta.solana.com")
            ProtocolContract.CLUSTER_DEVNET ->
                Uri.parse("https://api.devnet.solana.com")
            else ->
                Uri.parse("https://api.testnet.solana.com")
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

        override fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignTransactions(request))
            } else {
                request.completeWithDecline()
            }
        }

        override fun onSignMessagesRequest(request: SignMessagesRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignMessages(request))
            } else {
                request.completeWithDecline()
            }
        }

        override fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                val endpointUri = clusterToRpcUri(request.cluster)
                cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignAndSendTransactions(request, endpointUri))
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
        sealed class SignPayloads(override val request: SignPayloadsRequest) : MobileWalletAdapterRemoteRequest(request)
        data class SignTransactions(override val request: SignTransactionsRequest) : SignPayloads(request)
        data class SignMessages(override val request: SignMessagesRequest) : SignPayloads(request)
        data class SignAndSendTransactions(
            override val request: SignAndSendTransactionsRequest,
            val endpointUri: Uri,
            val signedTransactions: Array<ByteArray>? = null,
            val signatures: Array<ByteArray>? = null
        ) : MobileWalletAdapterRemoteRequest(request)
    }

    companion object {
        private val TAG = MobileWalletAdapterViewModel::class.simpleName
        private const val SOURCE_VERIFICATION_TIMEOUT_MS = 3000L
    }
}