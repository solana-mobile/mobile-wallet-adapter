/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.RxMobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val compositeDisposable = CompositeDisposable()

    private val mobileWalletAdapterClientSem =
        Semaphore(1) // allow only a single MWA connection at a time

    override fun onCleared() {
        compositeDisposable.dispose()
        super.onCleared()
    }

    fun authorize(sender: ActivityResultSender) {
        RxMobileWalletAdapter(Scenario.DEFAULT_CLIENT_TIMEOUT_MS, sender, null).apply {
            authorize(
                Uri.parse("https://solana.com"),
                Uri.parse("favicon.ico"),
                "Solana"
            ).subscribe(
                { result ->
                    Log.d(TAG, "Authorized: $result")
                    _uiState.update {
                        it.copy(
                            authToken = result.authToken,
                            publicKeyBase58 = result.publicKey
                        )
                    }
                },
                { throwable ->
                    when (throwable) {
                        is ExecutionException -> {
                            when (val cause = throwable.cause) {
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
                                else -> throw throwable
                            }
                        }
                        is CancellationException -> Log.e(TAG, "authorize request was cancelled", throwable)
                        is InterruptedException -> Log.e(TAG, "authorize request was interrupted", throwable)
                        else -> Log.e(TAG, "something went wrong", throwable)
                    }
                }
            ).apply { compositeDisposable.add(this) }
        }
    }

    suspend fun reauthorize(sender: StartActivityForResultSender) {
        localAssociateAndExecute(sender) { client ->
            doReauthorize(client)
        }
    }

    suspend fun deauthorize(sender: StartActivityForResultSender) {
        localAssociateAndExecute(sender) { client ->
            doDeauthorize(client)
        }
    }

    suspend fun getCapabilities(sender: StartActivityForResultSender) {
        localAssociateAndExecute(sender) { client ->
            doGetCapabilities(client)
        }
    }

    suspend fun signTransaction(sender: StartActivityForResultSender, numTransactions: Int) {
        val signedTransactions = localAssociateAndExecute(sender) { client ->
            val transactions = Array(numTransactions) {
                MemoTransaction.create(uiState.value.publicKeyBase58!!)
            }
            doSignTransaction(client, transactions)
        }

        signedTransactions?.let { txns ->
            txns.forEach { txn ->
                try {
                    MemoTransaction.verify(uiState.value.publicKeyBase58!!, txn)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Memo transaction signature verification failed", e)
                }
            }
        } ?: Log.w(TAG, "No signed transactions returned; skipping verification")
    }

    suspend fun authorizeAndSignTransaction(sender: StartActivityForResultSender) {
        val signedTransactions = localAssociateAndExecute(sender) { client ->
            val authorized = doAuthorize(client)
            if (authorized) {
                val transactions = Array(1) {
                    MemoTransaction.create(uiState.value.publicKeyBase58!!)
                }
                doSignTransaction(client, transactions)
            } else {
                null
            }
        }

        signedTransactions?.let { txns ->
            txns.forEach { txn ->
                try {
                    MemoTransaction.verify(uiState.value.publicKeyBase58!!, txn)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Memo transaction signature verification failed", e)
                }
            }
        } ?: Log.w(TAG, "No signed transactions returned; skipping verification")
    }

    suspend fun signMessage(sender: StartActivityForResultSender, numMessages: Int) {
        localAssociateAndExecute(sender) { client ->
            val messages = Array(numMessages) {
                Random.nextBytes(16384)
            }
            doSignMessage(client, messages)
        }
    }

    suspend fun signAndSendTransaction(sender: StartActivityForResultSender, numTransactions: Int) {
        localAssociateAndExecute(sender) { client ->
            val transactions = Array(numTransactions) {
                MemoTransaction.create(uiState.value.publicKeyBase58!!)
            }
            doSignAndSendTransaction(client, transactions)
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
    private fun doGetCapabilities(client: MobileWalletAdapterClient) {
        try {
            val result = client.getCapabilities().get()
            Log.d(TAG, "Capabilities: $result")
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
                uiState.value.authToken!!, transactions,
                CommitmentLevel.Confirmed, ProtocolContract.CLUSTER_TESTNET, false, null
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
                    signatures = cause.signatures
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
        action: (MobileWalletAdapterClient) -> T?
    ): T? {
        return mobileWalletAdapterClientSem.withPermit {
            val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)

            sender.startActivityForResult(LocalAssociationIntentCreator.createAssociationIntent(uriPrefix, localAssociation.port, localAssociation.session))

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
        fun startActivityForResult(intent: Intent)
    }

    data class UiState(
        val authToken: String? = null,
        val publicKeyBase58: String? = null
    ) {
        val hasAuthToken: Boolean get() = (authToken != null)
    }

    companion object {
        private val TAG = MainViewModel::class.simpleName
        private const val ASSOCIATION_TIMEOUT_MS = 10000L
    }
}