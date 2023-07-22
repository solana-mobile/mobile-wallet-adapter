package com.solanamobile.mobilewalletadapterwalletlib.reactnative

import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.scenario.*
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.BuildConfig
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.UUID

class SolanaMobileWalletAdapterWalletLibModule(val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), CoroutineScope {

    private val json = Json { ignoreUnknownKeys = true }

    // Sets the name of the module in React, accessible at ReactNative.NativeModules.SolanaMobileWalletAdapterWalletLib
    override fun getName() = "SolanaMobileWalletAdapterWalletLib"
    
    override val coroutineContext =
        Dispatchers.IO + CoroutineName("SolanaMobileWalletAdapterWalletLibModuleScope") + SupervisorJob()

    // Session events that notify about the lifecycle of the Scenario session. We are choosing
    // to go with the naming convention Session rather than Scenario for readability.
    sealed interface MobileWalletAdapterSessionEvent {
        val type: String
        object None : MobileWalletAdapterSessionEvent {
            override val type: String = ""
        }
        object SessionTerminated : MobileWalletAdapterSessionEvent {
            override val type: String = "SESSION_TERMINATED"
        }
        object ScenarioReady : MobileWalletAdapterSessionEvent {
            override val type: String = "SESSION_READY"
        }
        object ScenarioServingClients : MobileWalletAdapterSessionEvent {
            override val type: String = "SESSION_SERVING_CLIENTS"
        }
        object ScenarioServingComplete : MobileWalletAdapterSessionEvent {
            override val type: String = "SESSION_SERVING_COMPLETE"
        }
        object ScenarioComplete : MobileWalletAdapterSessionEvent {
            override val type: String = "SESSION_COMPLETE"
        }
        class ScenarioError(val message: String? = null) : MobileWalletAdapterSessionEvent {
            override val type: String = "SESSION_ERROR"
        }
        object ScenarioTeardownComplete : MobileWalletAdapterSessionEvent {
            override val type: String = "SESSION_TEARDOWN_COMPLETE"
        }
        object LowPowerNoConnection : MobileWalletAdapterSessionEvent {
            override val type: String = "LOW_POWER_NO_CONNECTION"
        }
    }

    // Service requests that come from the dApp for Authorization, Signing, Sending, hence "RemoteRequest".
    sealed class MobileWalletAdapterRemoteRequest(open val request: ScenarioRequest, 
                                                  val id: String = UUID.randomUUID().toString()) {
        data class AuthorizeDapp(override val request: AuthorizeRequest) : MobileWalletAdapterRemoteRequest(request)
        data class ReauthorizeDapp(override val request: ReauthorizeRequest) : MobileWalletAdapterRemoteRequest(request)
        data class DeauthorizeDapp(override val request: DeauthorizedEvent) : MobileWalletAdapterRemoteRequest(request)

        sealed class SignPayloads(override val request: SignPayloadsRequest) : MobileWalletAdapterRemoteRequest(request)
        data class SignTransactions(override val request: SignTransactionsRequest) : SignPayloads(request)
        data class SignMessages(override val request: SignMessagesRequest) : SignPayloads(request)
        data class SignAndSendTransactions(
            override val request: SignAndSendTransactionsRequest,
            val endpointUri: Uri,
        ) : MobileWalletAdapterRemoteRequest(request)
    }

    // currently we only allow a single scenario to exist at a time
    private var scenarioId: String? = null
    private var scenarioUri: Uri? = null
    private var scenario: Scenario? = null
        set(value) {
            value?.let { scenarioId = UUID.randomUUID().toString() } ?: run {
                scenarioId = null
                scenario?.close()
            }
            pendingRequests.clear()
            field = value
        }

    // very basic/naive implememtion of request cache. 
    // we could replace this with an abstraction that has expiration, persistance, etc. 
    private val pendingRequests = mutableMapOf<String, MobileWalletAdapterRemoteRequest>()

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

    @ReactMethod
    fun createScenario(
        walletName: String,
        uriStr: String,
        config: String,
    ) = launch {
        val uri = Uri.parse(uriStr)

        // TODO: this is dirty, need some stateful object/data to know what state we are in.
        //  also, should we suport multiple simulatneous scenario?
        if (uri == scenarioUri && scenario != null) {
            Log.w(TAG, "Session already created for uri: $uri")
            return@launch
        }

        val associationUri = AssociationUri.parse(uri)
        if (associationUri == null) {
            Log.e(TAG, "Unsupported association URI: $uri")
            return@launch
        } else if (associationUri !is LocalAssociationUri) {
            Log.e(TAG, "Current implementation of fakewallet does not support remote clients")
            return@launch
        }

        scenarioUri = uri

        val kotlinConfig = json.decodeFromString(MobileWalletAdapterConfigSerializer, config)

        // created a scenario, told it to start (kicks off some threads in the background)
        // we've kept a reference to it in the global state of this module (scenario)
        // this won't be garbage collected and will just run, sit & wait for an incoming connection
        scenario = associationUri.createScenario(
            reactContext,
            kotlinConfig,
            AuthIssuerConfig(walletName),
            MobileWalletAdapterScenarioCallbacks()
        ).also { it.start() }

        Log.d(TAG, "scenario created: $walletName")
    }

    /* Generic Request functions */
    @ReactMethod
    fun cancelRequest(sessionId: String, requestId: String) {
        Log.d(TAG, "Cancelled request $requestId")
        (pendingRequests.remove(requestId) as? ScenarioRequest)?.let { scenarioRequest ->
            scenarioRequest.cancel()
        }
    }

    @ReactMethod
    fun resolve(requestJson: String, responseJson: String) = launch {
        val completedRequest = json.decodeFromString(MobileWalletAdapterRequestSerializer, requestJson)
        val response = json.decodeFromString(MobileWalletAdapterResponseSerializer, responseJson)
        val pendingRequest = pendingRequests[completedRequest.requestId]

        if (completedRequest.sessionId != scenarioId) {
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioError(
                "Invalid session (${completedRequest.sessionId}). This session does not exist/is no longer active."
            ))
            return@launch
        }

        fun completeWithInvalidResponse() {
            pendingRequest?.request?.completeWithInternalError(Exception("Invalid Response For Request: response = $responseJson"))
        }

        when (completedRequest) {
            is AuthorizeDapp -> when (response) {
                is MobileWalletAdapterFailureResponse -> {
                    when (response) {
                        is UserDeclinedResponse ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.AuthorizeDapp)?.request?.completeWithDecline()
                        else -> completeWithInvalidResponse()
                    }
                }
                is AuthorizeDappResponse ->
                    (pendingRequest as? MobileWalletAdapterRemoteRequest.AuthorizeDapp)
                        ?.request?.completeWithAuthorize(
                            response.publicKey,
                            response.accountLabel,
                            null, //Uri.parse(response.walletUriBase),
                            response.authorizationScope
                        )
                else -> completeWithInvalidResponse()
            }
            is ReauthorizeDapp -> when (response) {
                is MobileWalletAdapterFailureResponse -> {
                    when (response) {
                        is AuthorizationNotValidResponse ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.ReauthorizeDapp)?.request?.completeWithDecline()
                        else -> completeWithInvalidResponse()
                    }
                }
                is ReauthorizeDappResponse ->
                    (pendingRequest as? MobileWalletAdapterRemoteRequest.ReauthorizeDapp)?.request?.completeWithReauthorize()
                else -> completeWithInvalidResponse()
            }
            is DeauthorizeDapp -> when (response) {
                is DeauthorizeDappResponse ->
                    (pendingRequest as? MobileWalletAdapterRemoteRequest.DeauthorizeDapp)?.request?.complete()
                else -> completeWithInvalidResponse()
            }
            is SignAndSendTransactions -> when (response) {
                is MobileWalletAdapterFailureResponse -> {
                    when (response) {
                        is UserDeclinedResponse  ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.completeWithDecline()
                        is TooManyPayloadsResponse ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.completeWithTooManyPayloads()
                        is AuthorizationNotValidResponse ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.completeWithAuthorizationNotValid()
                        is InvalidSignaturesResponse ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.completeWithInvalidSignatures(response.valid)
                    }
                }
                is SignedAndSentTransactions ->
                    (pendingRequest as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.completeWithSignatures(response.signedTransactions.toTypedArray())
                else -> completeWithInvalidResponse()
            }
            is SignPayloads -> when (response) {
                is MobileWalletAdapterFailureResponse -> {
                    when (response) {
                        is UserDeclinedResponse  ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.completeWithDecline()
                        is TooManyPayloadsResponse ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.completeWithTooManyPayloads()
                        is AuthorizationNotValidResponse ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.completeWithAuthorizationNotValid()
                        is InvalidSignaturesResponse ->
                            (pendingRequest as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.completeWithInvalidPayloads(response.valid)
                    }
                }
                is SignedPayloads ->
                    (pendingRequest as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.completeWithSignedPayloads(response.signedPayloads.toTypedArray())
                else -> completeWithInvalidResponse()
            }
        }
    }

    private fun checkSessionId(sessionId: String, doIfValid: (() -> Unit)) = 
        if (sessionId == scenarioId) doIfValid() 
        else sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioError(
            "Invalid session ($sessionId). This session does not exist/is no longer active."
        ))

    private fun sendSessionEventToReact(sessionEvent: MobileWalletAdapterSessionEvent) {
        val eventInfo = when(sessionEvent) {
            is MobileWalletAdapterSessionEvent.None -> null
            is MobileWalletAdapterSessionEvent.ScenarioError -> Arguments.createMap().apply {
                putString("__type", sessionEvent.type)
                putString("error", sessionEvent.message)
            }
            else -> Arguments.createMap().apply {
                putString("__type", sessionEvent.type)
            }
        }

        eventInfo?.putString("sessionId", scenarioId)

        eventInfo?.let { sendEvent(reactContext,
            Companion.MOBILE_WALLET_ADAPTER_SERVICE_REQUEST_BRIDGE_NAME, it) }
    }

    private fun sendWalletServiceRequestToReact(request: MobileWalletAdapterRemoteRequest) {
        val surrogate = when(request) {
            is MobileWalletAdapterRemoteRequest.AuthorizeDapp -> AuthorizeDapp(
                scenarioId!!, request.request.cluster, request.request.identityName,
                request.request.identityUri.toString(), request.request.iconRelativeUri.toString()
            )
            is MobileWalletAdapterRemoteRequest.ReauthorizeDapp -> ReauthorizeDapp(
                scenarioId!!, request.request.cluster, request.request.identityName,
                request.request.identityUri.toString(), request.request.iconRelativeUri.toString(),
                request.request.authorizationScope
            )
            is MobileWalletAdapterRemoteRequest.DeauthorizeDapp -> DeauthorizeDapp(
                scenarioId!!, request.request.cluster, request.request.identityName,
                request.request.identityUri.toString(), request.request.iconRelativeUri.toString(),
                request.request.authorizationScope
            )
            is MobileWalletAdapterRemoteRequest.SignMessages -> SignMessages(
                scenarioId!!, request.request.cluster, request.request.identityName,
                request.request.identityUri.toString(), request.request.iconRelativeUri.toString(),
                request.request.authorizationScope, request.request.payloads.toList()
            )
            is MobileWalletAdapterRemoteRequest.SignTransactions -> SignTransactions(
                scenarioId!!, request.request.cluster, request.request.identityName,
                request.request.identityUri.toString(), request.request.iconRelativeUri.toString(),
                request.request.authorizationScope, request.request.payloads.toList()
            )
            is MobileWalletAdapterRemoteRequest.SignAndSendTransactions -> SignAndSendTransactions(
                scenarioId!!, request.request.cluster, request.request.identityName,
                request.request.identityUri.toString(), request.request.iconRelativeUri.toString(),
                request.request.authorizationScope, request.request.payloads.toList()
            )
        }

        // this is dirty, the requestId needs to line up so have to manually overwrite here
        // should we change javascript side to accept json?
        val eventInfo = 
            JsonObject(json.encodeToJsonElement(MobileWalletAdapterRequestSerializer, surrogate)
                .jsonObject.toMutableMap().apply { 
                    put("requestId", JsonPrimitive(request.id))
                }).toReadableMap()

        sendEvent(reactContext, Companion.MOBILE_WALLET_ADAPTER_SERVICE_REQUEST_BRIDGE_NAME, eventInfo)
    }

    private fun sendEvent(reactContext: ReactContext, eventName: String, params: ReadableMap? = null) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private inner class MobileWalletAdapterScenarioCallbacks : LocalScenario.Callbacks {
        /* Session Events */
        override fun onScenarioReady() {
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioReady)
        }

        override fun onScenarioServingClients() {
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioServingClients)
        }

        override fun onScenarioServingComplete() {
            launch(Dispatchers.Main) {
                scenario = null
                sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioServingComplete)
            }
        }

        override fun onScenarioComplete() {
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioComplete)
        }

        override fun onScenarioError() {
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioError())
        }

        override fun onScenarioTeardownComplete() {
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioTeardownComplete)
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.SessionTerminated)
        }

        override fun onLowPowerAndNoConnection() {
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.LowPowerNoConnection)
        }

        /* Remote Requests */
        override fun onAuthorizeRequest(request: AuthorizeRequest) {
            val request = MobileWalletAdapterRemoteRequest.AuthorizeDapp(request)
            pendingRequests.put(request.id, request)
            sendWalletServiceRequestToReact(request)
        }

        override fun onReauthorizeRequest(request: ReauthorizeRequest) {
            val request = MobileWalletAdapterRemoteRequest.ReauthorizeDapp(request)
            pendingRequests.put(request.id, request)
            sendWalletServiceRequestToReact(request)
        }

        override fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            val request = MobileWalletAdapterRemoteRequest.SignTransactions(request)
            pendingRequests.put(request.id, request)
            sendWalletServiceRequestToReact(request)
        }

        override fun onSignMessagesRequest(request: SignMessagesRequest) {
            val request = MobileWalletAdapterRemoteRequest.SignMessages(request)
            pendingRequests.put(request.id, request)
            sendWalletServiceRequestToReact(request)
        }

        override fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
            val endpointUri = clusterToRpcUri(request.cluster)
            val request = MobileWalletAdapterRemoteRequest.SignAndSendTransactions(request, endpointUri)
            pendingRequests.put(request.id, request)
            sendWalletServiceRequestToReact(request)
        }

        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            val request = MobileWalletAdapterRemoteRequest.DeauthorizeDapp(event)
            pendingRequests.put(request.id, request)
            sendWalletServiceRequestToReact(request)
        }
    }

    companion object {
        private val TAG = SolanaMobileWalletAdapterWalletLibModule::class.simpleName
        const val MOBILE_WALLET_ADAPTER_SERVICE_REQUEST_BRIDGE_NAME = "MobileWalletAdapterServiceRequestBridge"
        const val MOBILE_WALLET_ADAPTER_SESSION_EVENT_BRIDGE_NAME = "MobileWalletAdapterSessionEventBridge"
    }
}