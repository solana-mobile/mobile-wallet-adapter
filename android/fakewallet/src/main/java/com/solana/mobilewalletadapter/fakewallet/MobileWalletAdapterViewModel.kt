/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.scenario.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
            getApplication<Application>().applicationContext,
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

        if (authorized) {
            request.request.completeWithAuthorize("somebase58publickey", Uri.parse(WALLET_BASE_URI))
        } else {
            request.request.completeWithDecline()
        }
    }

    fun signPayloadSimulateSign(request: MobileWalletAdapterServiceRequest.SignPayload) {
        if (rejectStaleRequest(request)) {
            return
        }
        val signedPayloads = Array(request.request.payloads.size) { i ->
            request.request.payloads[i].clone().also { it[0] = i.toByte() }
        }
        Log.d(TAG, "Simulating signing for ${request.request.publicKey}")
        request.request.completeWithSignedPayloads(signedPayloads)
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

    fun signAndSendTransactionSimulateSign(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        val signatures = Array(request.request.payloads.size) { i ->
            ByteArray(64) { i.toByte() }
        }
        Log.d(TAG, "Simulating signing for ${request.request.publicKey}")
        val requestWithSignatures = request.copy(signatures = signatures)
        if (rejectStaleRequest(request, requestWithSignatures)) {
            return
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
        request.request.completeWithSignatures(request.signatures!!)
    }

    fun signAndSendTransactionCommitmentNotReached(request: MobileWalletAdapterServiceRequest.SignAndSendTransaction) {
        if (rejectStaleRequest(request)) {
            return
        }
        val committed = BooleanArray(request.request.payloads.size) { i -> i != 0 }
        request.request.completeWithNotCommitted(request.signatures!!, committed)
    }

    private fun rejectStaleRequest(
        request: MobileWalletAdapterServiceRequest,
        replacement: MobileWalletAdapterServiceRequest = MobileWalletAdapterServiceRequest.None
    ): Boolean {
        if (!_mobileWalletAdapterServiceEvents.compareAndSet(request, replacement)) {
            Log.w(TAG, "Ignoring stale request")
            return true
        }
        return false
    }

    private inner class MobileWalletAdapterScenarioCallbacks : Scenario.Callbacks {
        override fun onScenarioReady() = Unit
        override fun onScenarioServingClients() = Unit
        override fun onScenarioServingComplete() {
            viewModelScope.launch(Dispatchers.Main) {
                scenario?.close()
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.None)
            }
        }
        override fun onScenarioComplete() = Unit
        override fun onScenarioError() = Unit
        override fun onScenarioTeardownComplete() {
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SessionTerminated)
            }
        }

        override fun onAuthorizeRequest(request: AuthorizeRequest) {
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.AuthorizeDapp(request))
            }
        }

        override fun onSignTransactionRequest(request: SignTransactionRequest) {
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SignTransaction(request))
            }
        }

        override fun onSignMessageRequest(request: SignMessageRequest) {
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SignMessage(request))
            }
        }

        override fun onSignAndSendTransactionRequest(request: SignAndSendTransactionRequest) {
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SignAndSendTransaction(request))
            }
        }
    }

    enum class AssocationType {
        LOCAL_FROM_BROWSER, LOCAL_FROM_APP, LOCAL_FROM_UNKNOWN, REMOTE
    }

    sealed interface MobileWalletAdapterServiceRequest {
        object None : MobileWalletAdapterServiceRequest
        data class AuthorizeDapp(val request: AuthorizeRequest) : MobileWalletAdapterServiceRequest
        sealed class SignPayload(val request: SignPayloadRequest) : MobileWalletAdapterServiceRequest
        class SignTransaction(request: SignTransactionRequest) : SignPayload(request)
        class SignMessage(request: SignMessageRequest) : SignPayload(request)
        data class SignAndSendTransaction(
            val request: SignAndSendTransactionRequest,
            val signatures: Array<ByteArray>? = null
        ) : MobileWalletAdapterServiceRequest
        object SessionTerminated : MobileWalletAdapterServiceRequest
    }

    companion object {
        private val TAG = MobileWalletAdapterViewModel::class.simpleName
        private const val WALLET_BASE_URI = "https://solanaexamplewallet.io/somepathprefix"
    }
}