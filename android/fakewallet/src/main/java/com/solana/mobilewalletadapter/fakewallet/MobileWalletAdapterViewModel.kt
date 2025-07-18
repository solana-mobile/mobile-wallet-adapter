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
import com.funkatronics.encoders.Base58
import com.funkatronics.encoders.Base64
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.SessionProperties
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import com.solana.mobilewalletadapter.fakewallet.usecase.*
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.authorization.AuthIssuerConfig
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import com.solana.mobilewalletadapter.walletlib.scenario.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.nio.charset.StandardCharsets

class MobileWalletAdapterViewModel(application: Application) : AndroidViewModel(application) {
    private val _mobileWalletAdapterServiceEvents =
        MutableStateFlow<MobileWalletAdapterServiceRequest>(MobileWalletAdapterServiceRequest.None)
    val mobileWalletAdapterServiceEvents =
        _mobileWalletAdapterServiceEvents.asSharedFlow() // expose as event stream, rather than a stateful object

    private var clientTrustUseCase: ClientTrustUseCase? = null
    private var scenario: Scenario? = null
    private var sessionId: String? = null

    fun isConnectionRemote(): Boolean = scenario is RemoteWebSocketServerScenario
    fun endSession() {
        scenario?.let {
            Log.d(TAG, "Ending active session: $sessionId")
            it.close()
        }
    }

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
        }

        clientTrustUseCase = ClientTrustUseCase(
            viewModelScope,
            getApplication<Application>().packageManager,
            callingPackage,
            associationUri
        )

        scenario = if (BuildConfig.PROTOCOL_VERSION == SessionProperties.ProtocolVersion.LEGACY
            && associationUri is LocalAssociationUri) {
            // manually create the scenario here so we can override the association protocol version
            // this forces ProtocolVersion.LEGACY to simulate a wallet using walletlib 1.x (for testing)
            LocalWebSocketServerScenario(
                getApplication<FakeWalletApplication>().applicationContext,
                MobileWalletAdapterConfig(
                    10,
                    10,
                    arrayOf(MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION, 0),
                    LOW_POWER_NO_CONNECTION_TIMEOUT_MS,
                    arrayOf(
                        ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS,
                        ProtocolContract.FEATURE_ID_SIGN_IN_WITH_SOLANA
                    )
                ),
                AuthIssuerConfig("fakewallet"),
                MobileWalletAdapterScenarioCallbacks(),
                associationUri.associationPublicKey,
                listOf(),
                associationUri.port,
            )
        } else {
            associationUri.createScenario(
                getApplication<FakeWalletApplication>().applicationContext,
                MobileWalletAdapterConfig(
                    10,
                    10,
                    arrayOf(MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION, 0),
                    LOW_POWER_NO_CONNECTION_TIMEOUT_MS,
                    arrayOf(
                        ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS,
                        ProtocolContract.FEATURE_ID_SIGN_IN_WITH_SOLANA
                    )
                ),
                AuthIssuerConfig("fakewallet"),
                MobileWalletAdapterScenarioCallbacks()
            )
        }.also {
            sessionId = null
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    sessionId = it.startAsync().get()
                }.getOrElse {
                    _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SessionEstablishmentFailed)
                }
            }
        }

        return true
    }

    override fun onCleared() {
        scenario?.close()
        scenario = null
    }

    fun authorizeDapp(
        request: MobileWalletAdapterServiceRequest.AuthorizationRequest,
        authorized: Boolean,
        numAccounts: Int = 1
    ) {
        if (rejectStaleRequest(request)) {
            return
        }

        viewModelScope.launch {
            if (authorized) {
                val accounts = (0 until numAccounts).map {
                    val publicKeyBytes = request.request.addresses?.get(it)?.let { address ->
                        val keypair = getApplication<FakeWalletApplication>().keyRepository
                            .getKeypair(Base64.decode(address)) ?: return@let null
                        val publicKey = keypair.public as Ed25519PublicKeyParameters
                        Log.d(TAG, "Reusing known keypair (pub=${publicKey.encoded.contentToString()}) for authorize request")
                        publicKey.encoded
                    } ?: run {
                        val keypair = getApplication<FakeWalletApplication>().keyRepository.generateKeypair()
                        val publicKey = keypair.public as Ed25519PublicKeyParameters
                        Log.d(TAG, "Generated a new keypair (pub=${publicKey.encoded.contentToString()}) for authorize request")
                        publicKey.encoded
                    }
                    buildAccount(publicKeyBytes, "fakewallet account $it")
                }
                request.request.completeWithAuthorize(accounts.toTypedArray(), null,
                    request.sourceVerificationState.authorizationScope.encodeToByteArray(), null)
            } else {
                request.request.completeWithDecline()
            }
        }
    }

    fun authorizeDappSimulateClusterNotSupported(
        request: MobileWalletAdapterServiceRequest.AuthorizeDapp
    ) {
        if (rejectStaleRequest(request)) {
            return
        }

        request.request.completeWithClusterNotSupported()
    }

    fun authorizationSimulateInternalError(
        request: MobileWalletAdapterServiceRequest.AuthorizationRequest
    ) {
        if (rejectStaleRequest(request)) {
            return
        }

        request.request.completeWithInternalError(RuntimeException("Internal error during authorize: -1234"))
    }

    fun signIn(
        request: MobileWalletAdapterServiceRequest.SignIn,
        authorizeSignIn: Boolean
    ) {
        if (rejectStaleRequest(request)) {
            return
        }

        viewModelScope.launch {
            if (authorizeSignIn) {
                val keypair = getApplication<FakeWalletApplication>().keyRepository.generateKeypair()
                val publicKey = keypair.public as Ed25519PublicKeyParameters
                Log.d(TAG, "Generated a new keypair (pub=${publicKey.encoded.contentToString()}) for authorize request")

                val siwsMessage = request.signInPayload.prepareMessage(publicKey.encoded)
                val signResult = try {
                    val messageBytes = siwsMessage.encodeToByteArray()
                    SolanaSigningUseCase.signMessage(messageBytes, listOf(keypair))
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "failed to sign SIWS payload", e)
                    request.request.completeWithInternalError(e)
                    return@launch
                }

                val signInResult = SignInResult(publicKey.encoded,
                    siwsMessage.encodeToByteArray(), signResult.signature, "ed25519")

                val account = buildAccount(publicKey.encoded, "fakewallet")
                request.request.completeWithAuthorize(arrayOf(account), null,
                    request.sourceVerificationState.authorizationScope.encodeToByteArray(), signInResult)
            } else {
                request.request.completeWithDecline()
            }
        }
    }

    fun signInSimulateSignInNotSupported(
        request: MobileWalletAdapterServiceRequest.SignIn
    ) {
        authorizeDapp(request, true)
    }

    fun signPayloadsSimulateSign(request: MobileWalletAdapterServiceRequest.SignPayloads) {
        if (rejectStaleRequest(request)) {
            return
        }

        viewModelScope.launch {

            val valid = BooleanArray(request.request.payloads.size) { true }
            val signedPayloads = when (request) {
                is MobileWalletAdapterServiceRequest.SignTransactions -> {
                    Array(request.request.payloads.size) { i ->
                        val tx = request.request.payloads[i]
                        val keypairs = SolanaSigningUseCase.getSignersForTransaction(tx).mapNotNull {
                            getApplication<FakeWalletApplication>().keyRepository.getKeypair(it)
                        }
                        Log.d(TAG, "Simulating transaction signing with ${keypairs.joinToString {
                            Base58.encodeToString((it.public as Ed25519PublicKeyParameters).encoded)
                        }}")
                        try {
                            SolanaSigningUseCase.signTransaction(tx, keypairs).signedPayload
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "Transaction [$i] is not a valid Solana transaction", e)
                            valid[i] = false
                            byteArrayOf()
                        }
                    }
                }
                is MobileWalletAdapterServiceRequest.SignMessages -> {
                    val keypairs = request.request.addresses.map {
                        val keypair = getApplication<FakeWalletApplication>().keyRepository.getKeypair(it)
                        check(keypair != null) { "Unknown public key for signing request" }
                        keypair
                    }
                    Log.d(TAG, "Simulating message signing with ${keypairs.joinToString {
                        Base58.encodeToString((it.public as Ed25519PublicKeyParameters).encoded)
                    }}")
                    Array(request.request.payloads.size) { i ->
                        // TODO: wallet should check that the payload is NOT a transaction
                        //  to ensure the user is not being tricked into signing a transaction
                        SolanaSigningUseCase.signMessage(request.request.payloads[i], keypairs).signedPayload
                    }
                }
            }

            if (valid.all { it }) {
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

    fun signPayloadsSimulateInternalError(request: MobileWalletAdapterServiceRequest.SignPayloads) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithInternalError(RuntimeException("Internal error during signing: -1234"))
    }

    fun signAndSendTransactionsSimulateSign(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        viewModelScope.launch {
            val signingResults = request.request.payloads.map { tx ->
                val keypairs = SolanaSigningUseCase.getSignersForTransaction(tx).mapNotNull {
                    getApplication<FakeWalletApplication>().keyRepository.getKeypair(it)
                }
                Log.d(TAG, "Simulating transaction signing with ${keypairs.joinToString {
                    Base58.encodeToString((it.public as Ed25519PublicKeyParameters).encoded)
                }}")
                try {
                    SolanaSigningUseCase.signTransaction(tx, keypairs)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "not a valid Solana transaction", e)
                    SolanaSigningUseCase.Result(byteArrayOf(), byteArrayOf())
                }
            }

            val valid = signingResults.map { result -> result.signature.isNotEmpty() }
            if (valid.all { it }) {
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

        Log.d(TAG, "Simulating transactions submitted on cluster=${request.request.chain}")

        request.request.completeWithSignatures(request.signatures!!)
    }

    fun signAndSendTransactionsNotSubmitted(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        if (rejectStaleRequest(request)) {
            return
        }

        Log.d(TAG, "Simulating transactions NOT submitted on cluster=${request.request.chain}")

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
                    request.request.minContextSlot,
                    request.request.commitment,
                    request.request.skipPreflight,
                    request.request.maxRetries
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

    fun signAndSendTransactionsSimulateInternalError(request: MobileWalletAdapterServiceRequest.SignAndSendTransactions) {
        if (rejectStaleRequest(request)) {
            return
        }
        request.request.completeWithInternalError(RuntimeException("Internal error during sign_and_send_transactions: -1234"))
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

    private fun buildAccount(publicKey: ByteArray, label: String, icon: Uri? = null,
                             chains: Array<String>? = null, features: Array<String>? = null ) =
        AuthorizedAccount(
            publicKey, Base58.encodeToString(publicKey), "base58",
            label, icon, chains, features
        )

    private fun chainOrClusterToRpcUri(chainOrCluster: String?): Uri {
        return when (chainOrCluster) {
            ProtocolContract.CHAIN_SOLANA_MAINNET,
            ProtocolContract.CLUSTER_MAINNET_BETA ->
                Uri.parse("https://api.mainnet-beta.solana.com")
            ProtocolContract.CHAIN_SOLANA_DEVNET,
            ProtocolContract.CLUSTER_DEVNET ->
                Uri.parse("https://api.devnet.solana.com")
            ProtocolContract.CHAIN_SOLANA_TESTNET,
            ProtocolContract.CLUSTER_TESTNET ->
                Uri.parse("https://api.testnet.solana.com")
            else -> throw IllegalArgumentException("Unsupported chain/cluster: $chainOrCluster")
        }
    }

    private inner class MobileWalletAdapterScenarioCallbacks : LocalScenario.Callbacks {
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
                if (sessionId != null) {
                    // No need to cancel any outstanding request; the scenario is torn down, and so
                    // cancelling a request that originated from it isn't actionable
                    _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.SessionTerminated)
                } else {
                    // Scenario has been torn down but we never established a session, cancel any previous request
                    cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SessionEstablishmentFailed)
                }
            }
        }

        override fun onAuthorizeRequest(request: AuthorizeRequest) {
            val clientTrustUseCase = clientTrustUseCase!! // should never be null if we get here

            val authorizationRequest = request.signInPayload?.let { signInPayload ->
                MobileWalletAdapterServiceRequest.SignIn(request, signInPayload,
                    clientTrustUseCase.verificationInProgress)
            } ?: MobileWalletAdapterServiceRequest.AuthorizeDapp(request,
                clientTrustUseCase.verificationInProgress)
            cancelAndReplaceRequest(authorizationRequest)

            val verify = clientTrustUseCase.verifyAuthorizationSourceAsync(request.identityUri)
            viewModelScope.launch {
                val verificationState = withTimeoutOrNull(SOURCE_VERIFICATION_TIMEOUT_MS) {
                    verify.await()
                } ?: clientTrustUseCase.verificationTimedOut

                if (!updateExistingRequest(
                        authorizationRequest,
                        when (authorizationRequest) {
                            is MobileWalletAdapterServiceRequest.AuthorizeDapp ->
                                authorizationRequest.copy(sourceVerificationState = verificationState)
                            is MobileWalletAdapterServiceRequest.SignIn ->
                                authorizationRequest.copy(sourceVerificationState = verificationState)
                        }
                    )
                ) {
                    return@launch
                }
            }
        }

        override fun onReauthorizeRequest(request: ReauthorizeRequest) {
            val reverify = clientTrustUseCase!!.verifyReauthorizationSourceAsync(
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
                val accounts = request.authorizedAccounts.filter { aa ->
                    request.addresses.any { it contentEquals aa.publicKey }
                }
                if (accounts.isEmpty()) {
                    request.completeWithAuthorizationNotValid()
                } else {
                    cancelAndReplaceRequest(MobileWalletAdapterServiceRequest.SignMessages(request))
                }
            } else {
                request.completeWithDecline()
            }
        }

        override fun onSignAndSendTransactionsRequest(request: SignAndSendTransactionsRequest) {
            if (verifyPrivilegedMethodSource(request)) {
                val endpointUri = chainOrClusterToRpcUri(request.chain)
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

        override fun onDeauthorizedEvent(event: DeauthorizedEvent) {
            Log.d(TAG, "'${event.identityName}' deauthorized")
            event.complete()
        }

        override fun onLowPowerAndNoConnection() {
            Log.w(TAG, "Device is in power save mode and no connection was made. The connection was likely suppressed by power save mode.")
            viewModelScope.launch {
                _mobileWalletAdapterServiceEvents.emit(MobileWalletAdapterServiceRequest.LowPowerNoConnection)
            }
        }
    }

    sealed interface MobileWalletAdapterServiceRequest {
        object None : MobileWalletAdapterServiceRequest
        object SessionTerminated : MobileWalletAdapterServiceRequest
        object LowPowerNoConnection : MobileWalletAdapterServiceRequest
        object SessionEstablishmentFailed : MobileWalletAdapterServiceRequest

        sealed class MobileWalletAdapterRemoteRequest(open val request: ScenarioRequest) : MobileWalletAdapterServiceRequest
        sealed class AuthorizationRequest(
            override val request: AuthorizeRequest,
            open val sourceVerificationState: ClientTrustUseCase.VerificationState
        ) : MobileWalletAdapterRemoteRequest(request)
        data class AuthorizeDapp(
            override val request: AuthorizeRequest,
            override val sourceVerificationState: ClientTrustUseCase.VerificationState
        ) : AuthorizationRequest(request, sourceVerificationState)
        data class SignIn(
            override val request: AuthorizeRequest,
            val signInPayload: SignInWithSolana.Payload,
            override val sourceVerificationState: ClientTrustUseCase.VerificationState
        ) : AuthorizationRequest(request, sourceVerificationState)
        sealed class SignPayloads(
            override val request: SignPayloadsRequest
        ) : MobileWalletAdapterRemoteRequest(request)
        data class SignTransactions(
            override val request: SignTransactionsRequest
        ) : SignPayloads(request)
        data class SignMessages(
            override val request: SignMessagesRequest,
//            val accounts: List<AuthorizedAccount>
        ) : SignPayloads(request)
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
        private const val LOW_POWER_NO_CONNECTION_TIMEOUT_MS = 3000L
    }
}