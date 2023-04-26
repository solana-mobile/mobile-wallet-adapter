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

class SolanaMobileWalletAdapterWalletLibModule(val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    // Sets the name of the module in React, accessible at ReactNative.NativeModules.SolanaMobileWalletAdapterWalletLib
    override fun getName() = "SolanaMobileWalletAdapterWalletLib"

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
        object ScenarioError : MobileWalletAdapterSessionEvent {
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
    sealed class MobileWalletAdapterRemoteRequest(open val request: ScenarioRequest) {
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

    private var request: MobileWalletAdapterRemoteRequest? = null
        set(value) {
            field = value
            value?.let { request -> sendWalletServiceRequestToReact(request) }
        }

    private var scenario: Scenario? = null

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

    // Converts a React ReadableArray into a Kotlin ByteArray.
    // Expects ReadableArray to be an Array of ints, where each int represents a byte.
    private fun convertFromReactByteArray(reactByteArray: ReadableArray): ByteArray {
        return ByteArray(reactByteArray.size()) { index ->
            reactByteArray.getInt(index).toByte()
        }
    }

    @ReactMethod
    fun createScenario(
        walletName: String,
        uriStr: String,
        config: ReadableMap
    ) {
        val uri = Uri.parse(uriStr)

        val associationUri = AssociationUri.parse(uri)
        if (associationUri == null) {
            Log.e(TAG, "Unsupported association URI: $uri")
            return
        } else if (associationUri !is LocalAssociationUri) {
            Log.e(TAG, "Current implementation of fakewallet does not support remote clients")
            return
        }

        // created a scenario, told it to start (kicks off some threads in the background)
        // we've kept a reference to it in the global state of this module (scenario)
        // this won't be garbage collected and will just run, sit & wait for an incoming connection
        scenario = associationUri.createScenario(
            reactContext,
            MobileWalletAdapterConfig(
                true,
                10,
                10,
                arrayOf(MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION, 0),
                3000L
            ),
            AuthIssuerConfig(walletName),
            MobileWalletAdapterScenarioCallbacks()
        ).also { it.start() }

        Log.d(TAG, "scenario created: $walletName")
    }

    /* Generic Request functions */
    @ReactMethod
    fun cancelRequest() {
        Log.d(TAG, "Cancelled request");
        (request as? ScenarioRequest)?.let { scenarioRequest ->
            scenarioRequest.cancel();
        }
    }

    /* AuthorizeDapp Request */
    @ReactMethod
    fun completeWithAuthorize(publicKey: ReadableArray, accountLabel: String?, walletUriBase: String?, authorizationScope: ReadableArray?) {
        Log.d(TAG, "completeWithAuthorize: authorized public key = $publicKey")
        (request as? MobileWalletAdapterRemoteRequest.AuthorizeDapp)?.request?.let { authRequest ->
            authRequest.completeWithAuthorize(
                convertFromReactByteArray(publicKey),
                accountLabel,
                null, // walletUriBase,
                null, // convertFromReactByteArray(authorizationScope)
            )
        }
    }

    @ReactMethod
    fun completeAuthorizeWithDecline() {
        Log.d(TAG, "completeAuthorizeWithDecline")
        (request as? MobileWalletAdapterRemoteRequest.AuthorizeDapp)?.request?.let { authRequest ->
            authRequest.completeWithDecline();
        }
    }

    /* SignPayloads Request */
    @ReactMethod
    fun completeWithSignedPayloads(signedPayloads: ReadableArray) {
        // signedPayloads is an Array of Number Arrays, with each inner Array representing
        // the bytes of a signed payload.
        Log.d(TAG, "completeSignPayloadsRequest: signedPayloads = $signedPayloads")
        (request as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
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

    @ReactMethod
    fun completeWithInvalidPayloads(validArray: ReadableArray) {
        Log.d(TAG, "completeWithInvalidPayloads: validArray = $validArray")
        val validBoolArray = BooleanArray(validArray.size()) { index -> validArray.getBoolean(index) }
        (request as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
            signRequest.completeWithInvalidPayloads(validBoolArray)
        }
    }

    @ReactMethod
    fun completeSignPayloadsWithDecline() {
        Log.d(TAG, "completeSignPayloadsWithDecline")
        (request as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
            signRequest.completeWithDecline();
        }
    }

    @ReactMethod
    fun completeSignPayloadsWithTooManyPayloads() {
        Log.d(TAG, "completeWithTooManyPayloads")
        (request as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
            signRequest.completeWithTooManyPayloads()
        }
    }

    @ReactMethod
    fun completeSignPayloadsWithAuthorizationNotValid() {
        Log.d(TAG, "completeSignPayloadsWithAuthorizationNotValid")
        (request as? MobileWalletAdapterRemoteRequest.SignPayloads)?.request?.let { signRequest ->
            signRequest.completeWithAuthorizationNotValid()
        }
    }

    /* SignAndSendTransactions Request */
    @ReactMethod
    fun completeWithSignatures(signaturesArray: ReadableArray) {
        Log.d(TAG, "completeWithSignatures: signatures = $signaturesArray")
        (request as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
            val signaturesNumArray = Arguments.toList(signaturesArray) as List<List<Number>>

            // Convert each Number Array into a ByteArray
            val signaturesByteArrays = signaturesNumArray.map { numArray ->
                ByteArray(numArray.size) { numArray[it].toByte() }
            }.toTypedArray()
            Log.d(TAG, "send signatures")
            signAndSendRequest.completeWithSignatures(signaturesByteArrays)
        }
    }

    @ReactMethod
    fun completeWithInvalidSignatures(validArray: ReadableArray) {
        Log.d(TAG, "completeWithInvalidSignatures: validArray = $validArray")
        val validBoolArray = BooleanArray(validArray.size()) { index -> validArray.getBoolean(index) }
        (request as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
            signAndSendRequest.completeWithInvalidSignatures(validBoolArray)
        }
    }

    @ReactMethod
    fun completeSignAndSendWithDecline() {
        Log.d(TAG, "completeSignAndSendWithDecline")
        (request as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
            signAndSendRequest.completeWithDecline();
        }
    }

    @ReactMethod
    fun completeSignAndSendWithTooManyPayloads() {
        Log.d(TAG, "completeSignAndSendWithTooManyPayloads")
        (request as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
            signAndSendRequest.completeWithTooManyPayloads()
        }
    }

    @ReactMethod
    fun completeSignAndSendWithAuthorizationNotValid() {
        Log.d(TAG, "completeSignAndSendWithAuthorizationNotValid")
        (request as? MobileWalletAdapterRemoteRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
            signAndSendRequest.completeWithAuthorizationNotValid()
        }
    }

    private fun sendSessionEventToReact(sessionEvent: MobileWalletAdapterSessionEvent) {
        val eventInfo = when(sessionEvent) {
            is MobileWalletAdapterSessionEvent.None -> null
            else -> Arguments.createMap().apply {
                putString("type", sessionEvent.type)
            }
        }

        eventInfo?.let { sendEvent(reactContext,
            Companion.MOBILE_WALLET_ADAPTER_SESSION_EVENT_BRIDGE_NAME, it) }
    }

    private fun sendWalletServiceRequestToReact(request: MobileWalletAdapterRemoteRequest) {
        val eventInfo = when(request) {
            is MobileWalletAdapterRemoteRequest.AuthorizeDapp -> Arguments.createMap().apply {
                putString("type", "AUTHORIZE_DAPP")
                request.request.getIdentityName()?.toString()?.let { identityName ->
                    putString("identityName", identityName)
                }
                request.request.getIdentityUri()?.toString()?.let { identityUri ->
                    putString("identityUri", identityUri)
                }
                request.request.getIconRelativeUri()?.toString()?.let { iconRelativeUri ->
                    putString("iconRelativeUri", iconRelativeUri)
                }
                putString("cluster", request.request.getCluster())
            }
            is MobileWalletAdapterRemoteRequest.ReauthorizeDapp -> Arguments.createMap().apply {
                putString("type", "REAUTHORIZE_DAPP")
            }
            is MobileWalletAdapterRemoteRequest.SignMessages -> Arguments.createMap().apply {
                putString("type", "SIGN_MESSAGES")
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
            }
            is MobileWalletAdapterRemoteRequest.SignTransactions -> Arguments.createMap().apply {
                putString("type", "SIGN_TRANSACTIONS")
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
            }
            is MobileWalletAdapterRemoteRequest.SignAndSendTransactions -> Arguments.createMap().apply {
                putString("type", "SIGN_AND_SEND_TRANSACTIONS")
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
                putString("minContextSlot", request.request.minContextSlot?.toString())
            }
        }

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
            scenario?.close()
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioServingComplete)
        }

        override fun onScenarioComplete() {
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioComplete)
        }

        override fun onScenarioError() {
            sendSessionEventToReact(MobileWalletAdapterSessionEvent.ScenarioError)
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
            this@SolanaMobileWalletAdapterWalletLibModule.request =
                MobileWalletAdapterRemoteRequest.AuthorizeDapp(request)
        }

        override fun onReauthorizeRequest(request: ReauthorizeRequest) {
            Log.i(TAG, "Reauthorization request: auto completing, DO NOT DO THIS IN PRODUCTION")
            // TODO: Implement client trust use case
            request.completeWithReauthorize()
        }

        override fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            this@SolanaMobileWalletAdapterWalletLibModule.request =
                MobileWalletAdapterRemoteRequest.SignTransactions(request)
        }

        override fun onSignMessagesRequest(request: SignMessagesRequest) {
            this@SolanaMobileWalletAdapterWalletLibModule.request =
                MobileWalletAdapterRemoteRequest.SignMessages(request)
        }

        override fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
            val endpointUri = clusterToRpcUri(request.cluster)
            this@SolanaMobileWalletAdapterWalletLibModule.request =
                MobileWalletAdapterRemoteRequest.SignAndSendTransactions(request, endpointUri)
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