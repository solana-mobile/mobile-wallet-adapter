/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel
import com.solana.mobilewalletadapter.fakedapp.usecase.GetLatestBlockhashUseCase
import com.solana.mobilewalletadapter.fakedapp.usecase.MemoTransactionUseCase
import com.solana.mobilewalletadapter.fakedapp.usecase.RequestAirdropUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val mobileWalletAdapterClientSem = Semaphore(1) // allow only a single MWA connection at a time

    fun authorize(sender: StartActivityForResultSender) = viewModelScope.launch {
        val result = localAssociateAndExecute(sender) { client ->
            doAuthorize(client)
        }

        showMessage(if (result == true) R.string.msg_request_succeeded else R.string.msg_request_failed)
    }

    fun reauthorize(sender: StartActivityForResultSender) = viewModelScope.launch {
        val result = localAssociateAndExecute(sender) { client ->
            doReauthorize(client)
        }

        showMessage(if (result == true) R.string.msg_request_succeeded else R.string.msg_request_failed)
    }

    fun deauthorize(sender: StartActivityForResultSender) = viewModelScope.launch {
        val result = localAssociateAndExecute(sender) { client ->
            doDeauthorize(client)
        }

        showMessage(if (result == true) R.string.msg_request_succeeded else R.string.msg_request_failed)
    }

    fun getCapabilities(sender: StartActivityForResultSender) = viewModelScope.launch {
        val result = localAssociateAndExecute(sender) { client ->
            doGetCapabilities(client)
        }

        showMessage(if (result != null) R.string.msg_request_succeeded else R.string.msg_request_failed)
    }

    fun requestAirdrop() = viewModelScope.launch {
        try {
            RequestAirdropUseCase(TESTNET_RPC_URI, _uiState.value.publicKeyBase58!!)
            Log.d(TAG, "Airdrop request sent")
            showMessage(R.string.msg_airdrop_request_sent)
        } catch (e: RequestAirdropUseCase.AirdropFailedException) {
            Log.e(TAG, "Airdrop request failed", e)
            showMessage(R.string.msg_airdrop_failed)
        }
    }

    fun signTransaction(sender: StartActivityForResultSender, numTransactions: Int) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO) {
            GetLatestBlockhashUseCase(TESTNET_RPC_URI)
        }

        val signedTransactions = localAssociateAndExecute(sender) { client ->
            val blockhash = try {
                latestBlockhash.await()
            } catch (e: GetLatestBlockhashUseCase.GetLatestBlockhashFailedException) {
                Log.e(TAG, "Failed retrieving latest blockhash", e)
                return@localAssociateAndExecute null
            }
            val transactions = Array(numTransactions) {
                MemoTransactionUseCase.create(uiState.value.publicKeyBase58!!, blockhash)
            }
            doSignTransaction(client, transactions)
        }

        if (signedTransactions != null) {
            val verified = signedTransactions.map { txn ->
                try {
                    MemoTransactionUseCase.verify(uiState.value.publicKeyBase58!!, txn)
                    true
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Memo transaction signature verification failed", e)
                    false
                }
            }

            showMessage(if (verified.all { it }) R.string.msg_request_succeeded else R.string.msg_signature_verification_failed)
        } else {
            Log.w(TAG, "No signed transactions returned; skipping verification")
            showMessage(R.string.msg_request_failed)
        }
    }

    fun authorizeAndSignTransaction(sender: StartActivityForResultSender) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO) {
            GetLatestBlockhashUseCase(TESTNET_RPC_URI)
        }

        val signedTransactions = localAssociateAndExecute(sender) { client ->
            val authorized = doAuthorize(client)
            if (authorized) {
                val blockhash = try {
                    latestBlockhash.await()
                } catch (e: GetLatestBlockhashUseCase.GetLatestBlockhashFailedException) {
                    Log.e(TAG, "Failed retrieving latest blockhash", e)
                    return@localAssociateAndExecute null
                }
                val transactions = Array(1) {
                    MemoTransactionUseCase.create(uiState.value.publicKeyBase58!!, blockhash)
                }
                doSignTransaction(client, transactions)
            } else {
                null
            }
        }

        if (signedTransactions != null) {
            val verified = signedTransactions.map { txn ->
                try {
                    MemoTransactionUseCase.verify(uiState.value.publicKeyBase58!!, txn)
                    true
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Memo transaction signature verification failed", e)
                    false
                }
            }

            showMessage(if (verified.all { it }) R.string.msg_request_succeeded else R.string.msg_signature_verification_failed)
        } else {
            Log.w(TAG, "No signed transactions returned; skipping verification")
            showMessage(R.string.msg_request_failed)
        }
    }

    fun signMessage(sender: StartActivityForResultSender, numMessages: Int) = viewModelScope.launch {
        val signedMessages = localAssociateAndExecute(sender) { client ->
            val messages = Array(numMessages) {
                Random.nextBytes(16384)
            }
            doSignMessage(client, messages)
        }

        showMessage(if (signedMessages != null) R.string.msg_request_succeeded else R.string.msg_request_failed)
    }

    fun signAndSendTransaction(sender: StartActivityForResultSender, numTransactions: Int) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO) {
            GetLatestBlockhashUseCase(TESTNET_RPC_URI)
        }

        val signatures = localAssociateAndExecute(sender) { client ->
            val blockhash = try {
                latestBlockhash.await()
            } catch (e: GetLatestBlockhashUseCase.GetLatestBlockhashFailedException) {
                Log.e(TAG, "Failed retrieving latest blockhash", e)
                return@localAssociateAndExecute null
            }
            val transactions = Array(numTransactions) {
                MemoTransactionUseCase.create(uiState.value.publicKeyBase58!!, blockhash)
            }
            doSignAndSendTransaction(client, transactions)
        }

        showMessage(if (signatures != null) R.string.msg_request_succeeded else R.string.msg_request_failed)
    }

    private fun showMessage(@StringRes resId: Int) {
        val str = getApplication<Application>().getString(resId)
        _uiState.update {
            it.copy(messages = it.messages.plus(str))
        }
    }

    fun messageShown() {
        _uiState.update {
            it.copy(messages = it.messages.drop(1))
        }
    }

    // NOTE: blocks and waits for completion of remote method call
    private fun doAuthorize(client: MobileWalletAdapterClient): Boolean {
        var authorized = false
        try {
            val result = client.authorize(
                Uri.parse("https://solana.com"),
                Uri.parse("favicon.ico"),
                "Solana"
            ).get()
            Log.d(TAG, "Authorized: $result")
            _uiState.update {
                it.copy(
                    authToken = result.authToken,
                    publicKeyBase58 = result.publicKey
                )
            }
            authorized = true
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException -> Log.e(TAG, "IO error while sending authorize", cause)
                is TimeoutException ->
                    Log.e(TAG, "Timed out while waiting for authorize result", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED ->
                            Log.e(TAG, "Not authorized", cause)
                        else ->
                            Log.e(TAG, "Remote exception for authorize", cause)
                    }
                is JsonRpc20Client.JsonRpc20Exception ->
                    Log.e(TAG, "JSON-RPC client exception for authorize", cause)
                else -> throw e
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "authorize request was cancelled", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "authorize request was interrupted", e)
        }

        return authorized
    }

    // NOTE: blocks and waits for completion of remote method call
    private fun doReauthorize(client: MobileWalletAdapterClient): Boolean {
        var reauthorized = false
        try {
            val result = client.reauthorize(
                Uri.parse("https://solana.com"),
                Uri.parse("favicon.ico"),
                "Solana",
                _uiState.value.authToken!!
            ).get()
            Log.d(TAG, "Reauthorized: $result")
            _uiState.update { it.copy(authToken = result.authToken) }
            reauthorized = true
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException -> Log.e(TAG, "IO error while sending reauthorize", cause)
                is TimeoutException ->
                    Log.e(TAG, "Timed out while waiting for reauthorize result", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> {
                            Log.e(TAG, "Not reauthorized", cause)
                            _uiState.update { it.copy(authToken = null) }
                        }
                        else ->
                            Log.e(TAG, "Remote exception for reauthorize", cause)
                    }
                is JsonRpc20Client.JsonRpc20Exception ->
                    Log.e(TAG, "JSON-RPC client exception for reauthorize", cause)
                else -> throw e
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "reauthorize request was cancelled", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "reauthorize request was interrupted", e)
        }

        return reauthorized
    }

    // NOTE: blocks and waits for completion of remote method call
    private fun doDeauthorize(client: MobileWalletAdapterClient): Boolean {
        var deauthorized = false
        try {
            client.deauthorize(_uiState.value.authToken!!).get()
            Log.d(TAG, "Deauthorized")
            _uiState.update { it.copy(authToken = null) }
            deauthorized = true
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException -> Log.e(TAG, "IO error while sending deauthorize", cause)
                is TimeoutException ->
                    Log.e(TAG, "Timed out while waiting for deauthorize result", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    Log.e(TAG, "Remote exception for deauthorize", cause)
                is JsonRpc20Client.JsonRpc20Exception ->
                    Log.e(TAG, "JSON-RPC client exception for deauthorize", cause)
                else -> throw e
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "deauthorize request was cancelled", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "deauthorize request was interrupted", e)
        }

        return deauthorized
    }

    // NOTE: blocks and waits for completion of remote method call
    private fun doGetCapabilities(client: MobileWalletAdapterClient): MobileWalletAdapterClient.GetCapabilitiesResult? {
        var capabilities: MobileWalletAdapterClient.GetCapabilitiesResult? = null

        try {
            val result = client.getCapabilities().get()
            Log.d(TAG, "Capabilities: $result")
            capabilities = result
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException -> Log.e(TAG, "IO error while sending get_capabilities", cause)
                is TimeoutException ->
                    Log.e(TAG, "Timed out while waiting for get_capabilities result", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    Log.e(TAG, "Remote exception for get_capabilities", cause)
                is JsonRpc20Client.JsonRpc20Exception ->
                    Log.e(TAG, "JSON-RPC client exception for get_capabilities", cause)
                else -> throw e
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "get_capabilities request was cancelled", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "get_capabilities request was interrupted", e)
        }

        return capabilities
    }

    // NOTE: blocks and waits for completion of remote method call
    private fun doSignTransaction(
        client: MobileWalletAdapterClient,
        transactions: Array<ByteArray>
    ): Array<ByteArray>? {
        var signedTransactions: Array<ByteArray>? = null
        try {
            val result = client.signTransaction(uiState.value.authToken!!, transactions).get()
            Log.d(TAG, "Signed transaction(s): $result")
            signedTransactions = result.signedPayloads
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException -> Log.e(TAG, "IO error while sending sign_transaction", cause)
                is TimeoutException ->
                    Log.e(TAG, "Timed out while waiting for sign_transaction result", cause)
                is MobileWalletAdapterClient.InvalidPayloadException ->
                    Log.e(TAG, "Transaction payload invalid", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_REAUTHORIZE -> Log.e(TAG, "Reauthorization required", cause)
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", cause)
                        ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", cause)
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", cause)
                        else -> Log.e(TAG, "Remote exception for sign_transaction", cause)
                    }
                is JsonRpc20Client.JsonRpc20Exception ->
                    Log.e(TAG, "JSON-RPC client exception for sign_transaction", cause)
                else -> throw e
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "sign_transaction request was cancelled", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "sign_transaction request was interrupted", e)
        }

        return signedTransactions
    }

    // NOTE: blocks and waits for completion of remote method call
    private fun doSignMessage(
        client: MobileWalletAdapterClient,
        messages: Array<ByteArray>
    ): Array<ByteArray>? {
        var signedMessages: Array<ByteArray>? = null
        try {
            val result = client.signMessage(uiState.value.authToken!!, messages).get()
            Log.d(TAG, "Signed message(s): $result")
            signedMessages = result.signedPayloads
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException -> Log.e(TAG, "IO error while sending sign_message", cause)
                is TimeoutException ->
                    Log.e(TAG, "Timed out while waiting for sign_message result", cause)
                is MobileWalletAdapterClient.InvalidPayloadException ->
                    Log.e(TAG, "Message payload invalid", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_REAUTHORIZE -> Log.e(TAG, "Reauthorization required", cause)
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", cause)
                        ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", cause)
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", cause)
                        else -> Log.e(TAG, "Remote exception for sign_message", cause)
                    }
                is JsonRpc20Client.JsonRpc20Exception ->
                    Log.e(TAG, "JSON-RPC client exception for sign_message", cause)
                else -> throw e
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "sign_message request was cancelled", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "sign_message request was interrupted", e)
        }

        return signedMessages
    }

    // NOTE: blocks and waits for completion of remote method call
    private fun doSignAndSendTransaction(
        client: MobileWalletAdapterClient,
        transactions: Array<ByteArray>
    ): Array<String>? {
        var signatures: Array<String>? = null
        try {
            val result = client.signAndSendTransaction(
                uiState.value.authToken!!,
                transactions,
                CommitmentLevel.Confirmed,
                ProtocolContract.CLUSTER_TESTNET,
                false,
                CommitmentLevel.Confirmed
            ).get()
            Log.d(TAG, "Signatures: ${result.signatures.contentToString()}")
            signatures = result.signatures
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException ->
                    Log.e(TAG, "IO error while sending sign_and_send_transaction", cause)
                is TimeoutException ->
                    Log.e(TAG, "Timed out while waiting for sign_and_send_transaction result", cause)
                is MobileWalletAdapterClient.InvalidPayloadException ->
                    Log.e(TAG, "Transaction payload invalid", cause)
                is MobileWalletAdapterClient.NotCommittedException -> {
                    Log.e(TAG, "Commitment not reached for all transactions", cause)
                }
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_REAUTHORIZE -> Log.e(TAG, "Reauthorization required", cause)
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", cause)
                        ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", cause)
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", cause)
                        else -> Log.e(TAG, "Remote exception for sign_and_send_transaction", cause)
                    }
                is JsonRpc20Client.JsonRpc20Exception ->
                    Log.e(TAG, "JSON-RPC client exception for sign_and_send_transaction", cause)
                else -> throw e
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "sign_and_send_transaction request was cancelled", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "sign_and_send_transaction request was interrupted", e)
        }

        return signatures
    }

    private suspend fun <T> localAssociateAndExecute(
        sender: StartActivityForResultSender,
        uriPrefix: Uri? = null,
        action: suspend (MobileWalletAdapterClient) -> T?
    ): T? {
        return mobileWalletAdapterClientSem.withPermit {
            val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)

            val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
                uriPrefix,
                localAssociation.port,
                localAssociation.session
            )
            try {
                sender.startActivityForResult(associationIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Failed to start intent=$associationIntent", e)
                showMessage(R.string.msg_no_wallet_found)
                return@withPermit null
            }

            return@withPermit withContext(Dispatchers.IO) {
                val mobileWalletAdapterClient = try {
                    @Suppress("BlockingMethodInNonBlockingContext") // running in Dispatchers.IO; blocking is appropriate
                    localAssociation.start().get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Interrupted while waiting for local association to be ready")
                    return@withContext null
                } catch (e: TimeoutException) {
                    Log.e(TAG, "Timed out waiting for local association to be ready")
                    return@withContext null
                } catch (e: ExecutionException) {
                    Log.e(TAG, "Failed establishing local association with wallet", e.cause)
                    return@withContext null
                }

                // NOTE: this is a blocking method call, appropriate in the Dispatchers.IO context
                val result = action(mobileWalletAdapterClient)

                @Suppress("BlockingMethodInNonBlockingContext") // running in Dispatchers.IO; blocking is appropriate
                localAssociation.close().get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                result
            }
        }
    }

    interface StartActivityForResultSender {
        fun startActivityForResult(intent: Intent) // throws ActivityNotFoundException
    }

    data class UiState(
        val authToken: String? = null,
        val publicKeyBase58: String? = null,
        val messages: List<String> = emptyList()
    ) {
        val hasAuthToken: Boolean get() = (authToken != null)
    }

    companion object {
        private val TAG = MainViewModel::class.simpleName
        private const val ASSOCIATION_TIMEOUT_MS = 10000L
        private val TESTNET_RPC_URI = Uri.parse("https://api.testnet.solana.com")
    }
}