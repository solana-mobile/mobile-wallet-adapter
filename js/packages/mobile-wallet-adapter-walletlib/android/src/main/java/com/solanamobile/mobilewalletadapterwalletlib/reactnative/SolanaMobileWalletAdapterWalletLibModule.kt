package com.solanamobile.mobilewalletadapterwalletlib.reactnative

import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.scenario.*
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.BuildConfig
import kotlinx.coroutines.*
import java.util.UUID

class SolanaMobileWalletAdapterWalletLibModule(val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), CoroutineScope {

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
        config: ReadableMap
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

        val kotlinConfig = config.toMobileWalletAdapterConfig()

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
        Log.d(TAG, "Cancelled request $requestId");
        (pendingRequests.remove(requestId) as? ScenarioRequest)?.let { scenarioRequest ->
            scenarioRequest.cancel();
        }
    }

    /* AuthorizeDapp Request */
    @ReactMethod
    fun completeWithAuthorize(
        sessionId: String, 
        requestId: String, 
        publicKey: ReadableArray, 
        accountLabel: String?, 
        walletUriBase: String?, 
        authorizationScope: ReadableArray?
    ) = launch {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeWithAuthorize: authorized public key = $publicKey")
            (pendingRequests.remove(requestId) as? MobileWalletAdapterRemoteRequest.AuthorizeDapp)?.request?.let { authRequest ->
                authRequest.completeWithAuthorize(
                    publicKey.toByteArray(),
                    accountLabel,
                    null, // walletUriBase,
                    null, // authorizationScope.toByteArray()
                )
            }
        }
    }

    @ReactMethod
    fun completeAuthorizeWithDecline(sessionId: String, requestId: String) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeAuthorizeWithDecline")
            (pendingRequests.remove(requestId) as? MobileWalletAdapterRemoteRequest.AuthorizeDapp)?.request?.let { authRequest ->
                authRequest.completeWithDecline();
            }
        }
    }

    /* SignPayloads Request */
    @ReactMethod
    fun completeWithSignedPayloads(sessionId: String, requestId: String, signedPayloads: ReadableArray) = launch {
        checkSessionId(sessionId) {
            // signedPayloads is an Array of Number Arrays, with each inner Array representing
            // the bytes of a signed payload.
            Log.d(TAG, "completeSignPayloadsRequest: signedPayloads = $signedPayloads")
            (pendingRequests.remove(requestId) as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
                // Convert ReadableArray to Array of Number Arrays
                val payloadNumArrays = Arguments.toList(signedPayloads) as List<List<Number>>

                // Convert each Number Array into a ByteArray
                val payloadByteArrays = payloadNumArrays.map { numArray ->
                    ByteArray(numArray.size) { numArray[it].toByte() }
                }.toTypedArray()

                Log.d(TAG, "signedPayload ByteArrays = $payloadByteArrays")
                signRequest.completeWithSignedPayloads(payloadByteArrays)
            }
        }
    }

    @ReactMethod
    fun completeWithInvalidPayloads(sessionId: String, requestId: String, validArray: ReadableArray) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeWithInvalidPayloads: validArray = $validArray")
            val validBoolArray = BooleanArray(validArray.size()) { index -> validArray.getBoolean(index) }
            (pendingRequests.remove(requestId) as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
                signRequest.completeWithInvalidPayloads(validBoolArray)
            }
        }
    }

    @ReactMethod
    fun completeSignPayloadsWithDecline(sessionId: String, requestId: String) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeSignPayloadsWithDecline")
            (pendingRequests.remove(requestId) as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
                signRequest.completeWithDecline()
            }
        }
    }

    @ReactMethod
    fun completeSignPayloadsWithTooManyPayloads(sessionId: String, requestId: String) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeWithTooManyPayloads")
            (pendingRequests.remove(requestId) as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
                signRequest.completeWithTooManyPayloads()
            }
        }
    }

    @ReactMethod
    fun completeSignPayloadsWithAuthorizationNotValid(sessionId: String, requestId: String) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeSignPayloadsWithAuthorizationNotValid")
            (pendingRequests.remove(requestId) as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
                signRequest.completeWithAuthorizationNotValid()
            }
        }
    }

    /* SignAndSendTransactions Request */
    @ReactMethod
    fun completeWithSignatures(sessionId: String, requestId: String, signaturesArray: ReadableArray) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeWithSignatures: signatures = $signaturesArray")
            (pendingRequests[requestId] as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
                val signaturesNumArray = Arguments.toList(signaturesArray) as List<List<Number>>

                // Convert each Number Array into a ByteArray
                val signaturesByteArrays = signaturesNumArray.map { numArray ->
                    ByteArray(numArray.size) { numArray[it].toByte() }
                }.toTypedArray()
                Log.d(TAG, "send signatures")
                signAndSendRequest.completeWithSignatures(signaturesByteArrays)
                pendingRequests.remove(requestId)
            }
        }
    }

    @ReactMethod
    fun completeWithInvalidSignatures(sessionId: String, requestId: String, validArray: ReadableArray) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeWithInvalidSignatures: validArray = $validArray")
            val validBoolArray = BooleanArray(validArray.size()) { index -> validArray.getBoolean(index) }
            (pendingRequests[requestId] as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
                signAndSendRequest.completeWithInvalidSignatures(validBoolArray)
                pendingRequests.remove(requestId)
            }
        }
    }

    @ReactMethod
    fun completeSignAndSendWithDecline(sessionId: String, requestId: String) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeSignAndSendWithDecline")
            (pendingRequests[requestId] as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
                signAndSendRequest.completeWithDecline()
                pendingRequests.remove(requestId)
            }
        }
    }

    @ReactMethod
    fun completeSignAndSendWithTooManyPayloads(sessionId: String, requestId: String) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeSignAndSendWithTooManyPayloads")
            (pendingRequests[requestId] as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
                signAndSendRequest.completeWithTooManyPayloads()
                pendingRequests.remove(requestId)
            }
        }
    }

    @ReactMethod
    fun completeSignAndSendWithAuthorizationNotValid(sessionId: String, requestId: String) {
        checkSessionId(sessionId) {
            Log.d(TAG, "completeSignAndSendWithAuthorizationNotValid")
            (pendingRequests[requestId] as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
                signAndSendRequest.completeWithAuthorizationNotValid()
                pendingRequests.remove(requestId)
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
        fun putCommonIdentityFields(map: WritableMap, request: MobileWalletAdapterRemoteRequest) {
            when(request.request) {
                is AuthorizeRequest -> {
                    val authRequest = request.request as AuthorizeRequest
                    authRequest.identityName?.toString()?.let { identityName ->
                        map.putString("identityName", identityName)
                    }
                    authRequest.identityUri?.toString()?.let { identityUri ->
                        map.putString("identityUri", identityUri)
                    }
                    authRequest.iconRelativeUri?.toString()?.let { iconRelativeUri ->
                        map.putString("iconRelativeUri", iconRelativeUri)
                    }
                }
                is VerifiableIdentityRequest -> {
                    val verifiableIdentityRequest = request.request as VerifiableIdentityRequest
                    verifiableIdentityRequest.identityName?.toString()?.let { identityName ->
                        map.putString("identityName", identityName)
                    }
                    verifiableIdentityRequest.identityUri?.toString()?.let { identityUri ->
                        map.putString("identityUri", identityUri)
                    }
                    verifiableIdentityRequest.iconRelativeUri?.toString()?.let { iconRelativeUri ->
                        map.putString("iconRelativeUri", iconRelativeUri)
                    }
                }
                else -> {
                    throw IllegalArgumentException("Request must be of type AuthorizeRequest or VerifiableIdentityRequest")
                }
            }
        }

        val eventInfo = when(request) {
            is MobileWalletAdapterRemoteRequest.AuthorizeDapp -> Arguments.createMap().apply {
                putString("__type", "AUTHORIZE_DAPP")
                putCommonIdentityFields(this, request)
                putString("cluster", request.request.cluster)
            }
            is MobileWalletAdapterRemoteRequest.ReauthorizeDapp -> Arguments.createMap().apply {
                putString("__type", "REAUTHORIZE_DAPP")
                putCommonIdentityFields(this, request)
                putString("cluster", request.request.cluster)
                putArray("authorizationScope", request.request.authorizationScope.toWritableArray())
            }
            is MobileWalletAdapterRemoteRequest.SignMessages -> Arguments.createMap().apply {
                putString("__type", "SIGN_MESSAGES")
                putCommonIdentityFields(this, request)
                putString("cluster", request.request.cluster)
                putArray("authorizationScope", request.request.authorizationScope.toWritableArray())
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
            }
            is MobileWalletAdapterRemoteRequest.SignTransactions -> Arguments.createMap().apply {
                putString("__type", "SIGN_TRANSACTIONS")
                putCommonIdentityFields(this, request)
                putString("cluster", request.request.cluster)
                putArray("authorizationScope", request.request.authorizationScope.toWritableArray())
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
            }
            is MobileWalletAdapterRemoteRequest.SignAndSendTransactions -> Arguments.createMap().apply {
                putString("__type", "SIGN_AND_SEND_TRANSACTIONS")
                putCommonIdentityFields(this, request)
                putString("cluster", request.request.cluster)
                putArray("authorizationScope", request.request.authorizationScope.toWritableArray())
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
                putString("minContextSlot", request.request.minContextSlot?.toString())
            }
        }

        eventInfo?.putString("sessionId", scenarioId)
        eventInfo?.putString("requestId", request.id)

        eventInfo?.let { params ->
            sendEvent(reactContext,
                Companion.MOBILE_WALLET_ADAPTER_SERVICE_REQUEST_BRIDGE_NAME, params)
        }
    }

    private fun sendEvent(reactContext: ReactContext, eventName: String, params: WritableMap? = null) {
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
            Log.i(TAG, "Reauthorization request: auto completing, DO NOT DO THIS IN PRODUCTION")
            // TODO: Implement client trust use case
            request.completeWithReauthorize()
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

        private fun verifyPrivilegedMethodSource(request: VerifiableIdentityRequest): Boolean {
            // TODO: Implement client trust use case
            return true
        }

        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            event.complete()
        }
    }

    companion object {
        private val TAG = SolanaMobileWalletAdapterWalletLibModule::class.simpleName
        const val MOBILE_WALLET_ADAPTER_SERVICE_REQUEST_BRIDGE_NAME = "MobileWalletAdapterServiceRequestBridge"
        const val MOBILE_WALLET_ADAPTER_SESSION_EVENT_BRIDGE_NAME = "MobileWalletAdapterSessionEventBridge"
    }
}