/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.lang.RuntimeException

class MobileWalletAdapterViewModel : ViewModel() {
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
            MobileWalletAdapterScenarioCallbacks(),
            MobileWalletAdapterMethods()
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
            request.request.complete(
                MobileWalletAdapterServer.AuthorizeResult(
                    Uri.parse(WALLET_BASE_URI)
                )
            )
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
        request.request.complete(MobileWalletAdapterServer.SignPayloadResult(signedPayloads))
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

    private fun rejectStaleRequest(request: MobileWalletAdapterServiceRequest): Boolean {
        if (!_mobileWalletAdapterServiceEvents.compareAndSet(
                request,
                MobileWalletAdapterServiceRequest.None
            )
        ) {
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
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SessionTerminated())
            }
        }
    }

    private inner class MobileWalletAdapterMethods : MobileWalletAdapterServer.MethodHandlers {
        override fun authorize(request: MobileWalletAdapterServer.AuthorizeRequest) {
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.AuthorizeDapp(request))
            }
        }

        override fun signTransaction(request: MobileWalletAdapterServer.SignPayloadRequest) {
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SignTransaction(request))
            }
        }

        override fun signMessage(request: MobileWalletAdapterServer.SignPayloadRequest) {
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SignMessage(request))
            }
        }
    }

    enum class AssocationType {
        LOCAL_FROM_BROWSER, LOCAL_FROM_APP, LOCAL_FROM_UNKNOWN, REMOTE
    }

    sealed interface MobileWalletAdapterServiceRequest {
        object None : MobileWalletAdapterServiceRequest
        data class AuthorizeDapp(val request: MobileWalletAdapterServer.AuthorizeRequest) :
            MobileWalletAdapterServiceRequest
        sealed class SignPayload(val request: MobileWalletAdapterServer.SignPayloadRequest) :
            MobileWalletAdapterServiceRequest
        class SignTransaction(request: MobileWalletAdapterServer.SignPayloadRequest) :
            SignPayload(request)
        class SignMessage(request: MobileWalletAdapterServer.SignPayloadRequest) :
            SignPayload(request)
        class SessionTerminated : MobileWalletAdapterServiceRequest
    }

    companion object {
        private val TAG = MobileWalletAdapterViewModel::class.simpleName
        private const val WALLET_BASE_URI = "https://solanaexamplewallet.io/somepathprefix"
    }
}