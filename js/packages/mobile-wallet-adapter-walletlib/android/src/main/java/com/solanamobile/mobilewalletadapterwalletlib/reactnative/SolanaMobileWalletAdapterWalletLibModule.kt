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
    ReactContextBaseJavaModule(reactContext) {//, CoroutineScope {

    // sets the name of the module in React, accessible at ReactNative.NativeModules.SolanaMobileWalletAdapterWalletLib
    override fun getName() = "SolanaMobileWalletAdapterWalletLib"

    sealed interface MobileWalletAdapterServiceRequest {
        object None : MobileWalletAdapterServiceRequest
        object SessionTerminated : MobileWalletAdapterServiceRequest
        object LowPowerNoConnection : MobileWalletAdapterServiceRequest

        sealed class MobileWalletAdapterRemoteRequest(open val request: ScenarioRequest) : MobileWalletAdapterServiceRequest
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

    private var request: MobileWalletAdapterServiceRequest? = null
        set(value) {
            field = value
            value?.let { request -> sendWalletRequestToReact(request) }
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

    // Converts a react "ReadableArray" into a Kotlin ByteArray.
    // Expects ReadableArray to be an Array of ints, where each int represents a byte.
    private fun convertFromReactByteArray(reactByteArray: ReadableArray): ByteArray {
        return ByteArray(reactByteArray.size()) { index ->
            reactByteArray.getInt(index).toByte()
        }
    }

    @ReactMethod
    fun log(message: String) {
        Log.d(TAG, "message from react: $message")
    }

    @ReactMethod
    fun createScenario(
        walletName: String, // our wallet's name (Backpack)
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


    @ReactMethod
    fun closeScenario() {
        scenario?.close();
    }

    @ReactMethod
    fun cancelRequest() {
        Log.d(TAG, "Cancelled request");
        (request as? ScenarioRequest)?.let { scenarioRequest ->
            scenarioRequest.cancel();
        }
    }

    /* AuthorizeDapp Request */
    @ReactMethod
    fun authorizeDapp(publicKey: ReadableArray) {
        Log.d(TAG, "authorizeDapp: authorized public key = $publicKey")
        (request as? MobileWalletAdapterServiceRequest.AuthorizeDapp)?.request?.let { authRequest ->
            authRequest.completeWithAuthorize(
                Arguments.toList(publicKey)?.let { shouldBeBytes ->
                    ByteArray(shouldBeBytes.size) {
                        (shouldBeBytes.get(it) as? Number)?.toByte() ?: 0
                    }
                }!!,
                "Backpack",
                null,
                null
            )
        }
    }

    @ReactMethod
    fun completeWithAuthorize(publicKey: ReadableArray, accountLabel: String?, walletUriBase: String?, authorizationScope: ReadableArray?) {
        Log.d(TAG, "completeWithAuthorize: authorized public key = $publicKey")
        (request as? MobileWalletAdapterServiceRequest.AuthorizeDapp)?.request?.let { authRequest ->
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
        (request as? MobileWalletAdapterServiceRequest.AuthorizeDapp)?.request?.let { authRequest ->
            authRequest.completeWithDecline();
        }
    }

    /* SignPayloads Request */
    @ReactMethod
    fun completeWithSignedPayloads(signedPayloads: ReadableArray) {
        // signedPayloads is an Array of Number Arrays, with each inner Array representing
        // the bytes of a signed payload.
        Log.d(TAG, "completeSignPayloadsRequest: signedPayloads = $signedPayloads")
        (request as? MobileWalletAdapterServiceRequest.SignPayloads)?.request?.let { signRequest ->
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
        (request as? MobileWalletAdapterServiceRequest.SignPayloads)?.request?.let { signRequest ->
            signRequest.completeWithInvalidPayloads(validBoolArray)
        }
    }
    
    @ReactMethod
    fun completeSignPayloadsWithDecline() {
        Log.d(TAG, "completeSignPayloadsWithDecline")
        (request as? MobileWalletAdapterServiceRequest.SignPayloads)?.request?.let { signRequest ->
            signRequest.completeWithDecline();
        }
    }

    @ReactMethod
    fun completeSignPayloadsWithTooManyPayloads() {
        Log.d(TAG, "completeWithTooManyPayloads")
        (request as? MobileWalletAdapterServiceRequest.SignPayloads)?.request?.let { signRequest ->
            signRequest.completeWithTooManyPayloads()
        }
    }

    @ReactMethod
    fun completeSignPayloadsWithAuthorizationNotValid() {
        Log.d(TAG, "completeSignPayloadsWithAuthorizationNotValid")
        (request as? MobileWalletAdapterServiceRequest.SignPayloads)?.request?.let { signRequest ->
            signRequest.completeWithAuthorizationNotValid()
        }
    }

    /* SignAndSendTransactions Request */
    @ReactMethod
    fun completeWithSignatures(signaturesArray: ReadableArray) {
        Log.d(TAG, "completeWithSignatures: signatures = $signaturesArray")
        (request as? MobileWalletAdapterServiceRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
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
        (request as? MobileWalletAdapterServiceRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
            signAndSendRequest.completeWithInvalidSignatures(validBoolArray)
        }
    }

    @ReactMethod
    fun completeSignAndSendWithDecline() {
        Log.d(TAG, "completeSignAndSendWithDecline")
        (request as? MobileWalletAdapterServiceRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
            signAndSendRequest.completeWithDecline();
        }
    }

    @ReactMethod
    fun completeSignAndSendWithTooManyPayloads() {
        Log.d(TAG, "completeSignAndSendWithTooManyPayloads")
        (request as? MobileWalletAdapterServiceRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
            signAndSendRequest.completeWithTooManyPayloads()
        }
    }

    @ReactMethod
    fun completeSignAndSendWithAuthorizationNotValid() {
        Log.d(TAG, "completeSignAndSendWithAuthorizationNotValid")
        (request as? MobileWalletAdapterServiceRequest.SignAndSendTransactions)?.request?.let { signAndSendRequest ->
            signAndSendRequest.completeWithAuthorizationNotValid()
        }
    }
    
    private fun sendWalletRequestToReact(request: MobileWalletAdapterServiceRequest) {
        // pretty dirty implementation :thug lyfe:
        val eventInfo = when(request) {
            is MobileWalletAdapterServiceRequest.None -> null
            is MobileWalletAdapterServiceRequest.SessionTerminated -> Arguments.createMap().apply {
                putString("type", "SESSION_TERMINATED")
            }
            is MobileWalletAdapterServiceRequest.LowPowerNoConnection -> Arguments.createMap().apply {
                putString("type", "LOW_POWER_NO_CONNECTION")
            }
            is MobileWalletAdapterServiceRequest.AuthorizeDapp -> Arguments.createMap().apply {
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
            is MobileWalletAdapterServiceRequest.ReauthorizeDapp -> Arguments.createMap().apply {
                putString("type", "REAUTHORIZE_DAPP")
            }
            is MobileWalletAdapterServiceRequest.SignMessages -> Arguments.createMap().apply {
                putString("type", "SIGN_MESSAGES")
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
            }
            is MobileWalletAdapterServiceRequest.SignTransactions -> Arguments.createMap().apply {
                putString("type", "SIGN_TRANSACTIONS")
                putArray("payloads", Arguments.createArray().apply {
                    request.request.payloads.map {
                        Arguments.fromArray(it.map { it.toInt() }.toIntArray())
                    }.forEach { pushArray(it) }
                })
            }
            is MobileWalletAdapterServiceRequest.SignAndSendTransactions -> Arguments.createMap().apply {
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
            sendEvent(reactContext, "MobileWalletAdapterServiceEvent", params)
        }
    }

    private fun sendEvent(reactContext: ReactContext, eventName: String, params: WritableMap? = null) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private inner class MobileWalletAdapterScenarioCallbacks : LocalScenario.Callbacks {
        override fun onScenarioReady() = Unit
        override fun onScenarioServingClients() = Unit
        override fun onScenarioServingComplete() {
            scenario?.close()
            this@SolanaMobileWalletAdapterWalletLibModule.request = null
        }

        override fun onScenarioComplete() = Unit
        override fun onScenarioError() = Unit
        override fun onScenarioTeardownComplete() {
            this@SolanaMobileWalletAdapterWalletLibModule.request =
                MobileWalletAdapterServiceRequest.SessionTerminated
        }

        override fun onAuthorizeRequest(request: AuthorizeRequest) {
            this@SolanaMobileWalletAdapterWalletLibModule.request =
                MobileWalletAdapterServiceRequest.AuthorizeDapp(request)
        }

        override fun onReauthorizeRequest(request: ReauthorizeRequest) {
            Log.i(TAG, "Reauthorization request: auto completing, DO NOT DO THIS IN PRODUCTION")
            // TODO: Implement client trust use case
            request.completeWithReauthorize()
        }

        override fun onSignTransactionsRequest(request: SignTransactionsRequest) {
            this@SolanaMobileWalletAdapterWalletLibModule.request =
                MobileWalletAdapterServiceRequest.SignTransactions(request)
        }

        override fun onSignMessagesRequest(request: SignMessagesRequest) {
            this@SolanaMobileWalletAdapterWalletLibModule.request =
                MobileWalletAdapterServiceRequest.SignMessages(request)
        }

        override fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
            val endpointUri = clusterToRpcUri(request.cluster)
            this@SolanaMobileWalletAdapterWalletLibModule.request =
                MobileWalletAdapterServiceRequest.SignAndSendTransactions(request, endpointUri)
        }

        private fun verifyPrivilegedMethodSource(request: VerifiableIdentityRequest): Boolean {
            // TODO: Implement client trust use case
            return true
        }

        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            Log.d(TAG, "'${event.identityName}' deauthorized")
            event.complete()
        }

        override fun onLowPowerAndNoConnection() {
            Log.w(TAG, "Device is in power save mode and no connection was made. The connection was likely suppressed by power save mode.")
            // TODO: should notify react so it can draw UI informing the user
        }
    }

    companion object {
        private val TAG = SolanaMobileWalletAdapterWalletLibModule::class.simpleName
    }
}