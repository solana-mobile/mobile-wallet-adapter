/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val mobileWalletAdapterClientMutex = Mutex()

    suspend fun authorize(sender: StartActivityForResultSender) {
        localAssociateAndExecute(sender) { client ->
            doAuthorize(client)
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

    private suspend fun doAuthorize(client: MobileWalletAdapterClient): Boolean {
        var authorized = false
        try {
            val sem = Semaphore(1, 1)
            // Note: not actually a blocking call - this check triggers on the thrown IOException,
            // which occurs when the client is not connected
            @Suppress("BlockingMethodInNonBlockingContext")
            val future = client.authorizeAsync(
                Uri.parse("https://solana.com"),
                Uri.parse("favicon.ico"),
                "Solana"
            )
            future.notifyOnComplete { sem.release() }
            sem.acquire()
            val result = try {
                // Note: this call won't block, since we've received the completion notification
                @Suppress("BlockingMethodInNonBlockingContext")
                future.get()
            } catch (e: ExecutionException) {
                throw MobileWalletAdapterClient.unpackExecutionException(e)
            }
            Log.d(TAG, "Authorized: $result")
            _uiState.update {
                it.copy(
                    authToken = result.authToken,
                    publicKeyBase58 = result.publicKey
                )
            }
            authorized = true
        } catch (e: IOException) {
            Log.e(TAG, "IO error while sending authorize", e)
        } catch (e: JsonRpc20Client.JsonRpc20RemoteException) {
            when (e.code) {
                ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Not authorized", e)
                else -> Log.e(TAG, "Remote exception for authorize", e)
            }
        } catch (e: JsonRpc20Client.JsonRpc20Exception) {
            Log.e(TAG, "JSON-RPC client exception for authorize", e)
        } catch (e: TimeoutException) {
            Log.e(TAG, "Timed out while waiting for authorize result", e)
        } catch (e: CancellationException) {
            Log.e(TAG, "authorize request was cancelled", e)
        }

        return authorized
    }

    private suspend fun doReauthorize(client: MobileWalletAdapterClient): Boolean {
        var reauthorized = false
        try {
            val sem = Semaphore(1, 1)
            // Note: not actually a blocking call - this check triggers on the thrown IOException,
            // which occurs when the client is not connected
            @Suppress("BlockingMethodInNonBlockingContext")
            val future = client.reauthorizeAsync(
                Uri.parse("https://solana.com"),
                Uri.parse("favicon.ico"),
                "Solana",
                _uiState.value.authToken!!
            )
            future.notifyOnComplete { sem.release() }
            sem.acquire()
            val result = try {
                // Note: this call won't block, since we've received the completion notification
                @Suppress("BlockingMethodInNonBlockingContext")
                future.get()
            } catch (e: ExecutionException) {
                throw MobileWalletAdapterClient.unpackExecutionException(e)
            }
            Log.d(TAG, "Reauthorized: $result")
            _uiState.update { it.copy(authToken = result.authToken) }
            reauthorized = true
        } catch (e: IOException) {
            Log.e(TAG, "IO error while sending reauthorize", e)
        } catch (e: JsonRpc20Client.JsonRpc20RemoteException) {
            when (e.code) {
                ProtocolContract.ERROR_AUTHORIZATION_FAILED -> {
                    Log.e(TAG, "Not reauthorized", e)
                    _uiState.update { it.copy(authToken = null) }
                }
                else -> Log.e(TAG, "Remote exception for reauthorize", e)
            }
        } catch (e: JsonRpc20Client.JsonRpc20Exception) {
            Log.e(TAG, "JSON-RPC client exception for reauthorize", e)
        } catch (e: TimeoutException) {
            Log.e(TAG, "Timed out while waiting for reauthorize result", e)
        } catch (e: CancellationException) {
            Log.e(TAG, "reauthorize request was cancelled", e)
        }

        return reauthorized
    }

    private suspend fun doDeauthorize(client: MobileWalletAdapterClient): Boolean {
        var deauthorized = false
        try {
            val sem = Semaphore(1, 1)
            // Note: not actually a blocking call - this check triggers on the thrown IOException,
            // which occurs when the client is not connected
            @Suppress("BlockingMethodInNonBlockingContext")
            val future = client.deauthorizeAsync(_uiState.value.authToken!!)
            future.notifyOnComplete { sem.release() }
            sem.acquire()
            try {
                // Note: this call won't block, since we've received the completion notification
                @Suppress("BlockingMethodInNonBlockingContext")
                future.get()
            } catch (e: ExecutionException) {
                throw MobileWalletAdapterClient.unpackExecutionException(e)
            }
            Log.d(TAG, "Deauthorized")
            _uiState.update { it.copy(authToken = null) }
            deauthorized = true
        } catch (e: IOException) {
            Log.e(TAG, "IO error while sending deauthorize", e)
        } catch (e: JsonRpc20Client.JsonRpc20RemoteException) {
            Log.e(TAG, "Remote exception for deauthorize", e)
        } catch (e: JsonRpc20Client.JsonRpc20Exception) {
            Log.e(TAG, "JSON-RPC client exception for deauthorize", e)
        } catch (e: TimeoutException) {
            Log.e(TAG, "Timed out while waiting for deauthorize result", e)
        } catch (e: CancellationException) {
            Log.e(TAG, "deauthorize request was cancelled", e)
        }

        return deauthorized
    }

    private suspend fun doGetCapabilities(client: MobileWalletAdapterClient) {
        try {
            val sem = Semaphore(1, 1)
            // Note: not actually a blocking call - this check triggers on the thrown IOException,
            // which occurs when the client is not connected
            @Suppress("BlockingMethodInNonBlockingContext")
            val future = client.getCapabilitiesAsync()
            future.notifyOnComplete { sem.release() }
            sem.acquire()
            val result = try {
                // Note: this call won't block, since we've received the completion notification
                @Suppress("BlockingMethodInNonBlockingContext")
                future.get()
            } catch (e: ExecutionException) {
                throw MobileWalletAdapterClient.unpackExecutionException(e)
            }
            Log.d(TAG, "Capabilities: $result")
        } catch (e: IOException) {
            Log.e(TAG, "IO error while sending get_capabilities", e)
        } catch (e: JsonRpc20Client.JsonRpc20RemoteException) {
            Log.e(TAG, "Remote exception for get_capabilities", e)
        } catch (e: JsonRpc20Client.JsonRpc20Exception) {
            Log.e(TAG, "JSON-RPC client exception for get_capabilities", e)
        } catch (e: TimeoutException) {
            Log.e(TAG, "Timed out while waiting for get_capabilities result", e)
        } catch (e: CancellationException) {
            Log.e(TAG, "get_capabilities request was cancelled", e)
        }
    }

    private suspend fun doSignTransaction(
        client: MobileWalletAdapterClient,
        transactions: Array<ByteArray>
    ): Array<ByteArray>? {
        var signedTransactions: Array<ByteArray>? = null
        try {
            val sem = Semaphore(1, 1)
            // Note: not actually a blocking call - this check triggers on the thrown IOException,
            // which occurs when the client is not connected
            @Suppress("BlockingMethodInNonBlockingContext")
            val future = client.signTransactionAsync(uiState.value.authToken!!, transactions)
            future.notifyOnComplete { sem.release() }
            sem.acquire()
            val result = try {
                // Note: this call won't block, since we've received the completion notification
                @Suppress("BlockingMethodInNonBlockingContext")
                future.get()
            } catch (e: ExecutionException) {
                throw MobileWalletAdapterClient.unpackExecutionException(e)
            }
            Log.d(TAG, "Signed transaction(s): $result")
            signedTransactions = result.signedPayloads
        } catch (e: IOException) {
            Log.e(TAG, "IO error while sending sign_transaction", e)
        } catch (e: MobileWalletAdapterClient.InvalidPayloadException) {
            Log.e(TAG, "Transaction payload invalid", e)
        } catch (e: JsonRpc20Client.JsonRpc20RemoteException) {
            when (e.code) {
                ProtocolContract.ERROR_REAUTHORIZE -> Log.e(TAG, "Reauthorization required", e)
                ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", e)
                ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", e)
                ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", e)
                else -> Log.e(TAG, "Remote exception for authorize", e)
            }
        } catch (e: JsonRpc20Client.JsonRpc20Exception) {
            Log.e(TAG, "JSON-RPC client exception for sign_transaction", e)
        } catch (e: TimeoutException) {
            Log.e(TAG, "Timed out while waiting for sign_transaction result", e)
        } catch (e: CancellationException) {
            Log.e(TAG, "sign_transaction request was cancelled", e)
        }
        return signedTransactions
    }

    private suspend fun doSignMessage(
        client: MobileWalletAdapterClient,
        messages: Array<ByteArray>
    ): Array<ByteArray>? {
        var signedMessages: Array<ByteArray>? = null
        try {
            val sem = Semaphore(1, 1)
            // Note: not actually a blocking call - this check triggers on the thrown IOException,
            // which occurs when the client is not connected
            @Suppress("BlockingMethodInNonBlockingContext")
            val future = client.signMessageAsync(uiState.value.authToken!!, messages)
            future.notifyOnComplete { sem.release() }
            sem.acquire()
            val result = try {
                // Note: this call won't block, since we've received the completion notification
                @Suppress("BlockingMethodInNonBlockingContext")
                future.get()
            } catch (e: ExecutionException) {
                throw MobileWalletAdapterClient.unpackExecutionException(e)
            }
            Log.d(TAG, "Signed message(s): $result")
            signedMessages = result.signedPayloads
        } catch (e: IOException) {
            Log.e(TAG, "IO error while sending sign_message", e)
        } catch (e: MobileWalletAdapterClient.InvalidPayloadException) {
            Log.e(TAG, "Message payload invalid", e)
        } catch (e: JsonRpc20Client.JsonRpc20RemoteException) {
            when (e.code) {
                ProtocolContract.ERROR_REAUTHORIZE -> Log.e(TAG, "Reauthorization required", e)
                ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", e)
                ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", e)
                ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", e)
                else -> Log.e(TAG, "Remote exception for sign_message", e)
            }
        } catch (e: JsonRpc20Client.JsonRpc20Exception) {
            Log.e(TAG, "JSON-RPC client exception for sign_message", e)
        } catch (e: TimeoutException) {
            Log.e(TAG, "Timed out while waiting for sign_message result", e)
        } catch (e: CancellationException) {
            Log.e(TAG, "sign_message request was cancelled", e)
        }
        return signedMessages
    }

    private suspend fun doSignAndSendTransaction(
        client: MobileWalletAdapterClient,
        transactions: Array<ByteArray>
    ): Array<String>? {
        var signatures: Array<String>? = null
        try {
            val sem = Semaphore(1, 1)
            // Note: not actually a blocking call - this check triggers on the thrown IOException,
            // which occurs when the client is not connected
            @Suppress("BlockingMethodInNonBlockingContext")
            val future = client.signAndSendTransactionAsync(uiState.value.authToken!!, transactions,
                CommitmentLevel.Confirmed)
            future.notifyOnComplete { sem.release() }
            sem.acquire()
            val result = try {
                // Note: this call won't block, since we've received the completion notification
                @Suppress("BlockingMethodInNonBlockingContext")
                future.get()
            } catch (e: ExecutionException) {
                throw MobileWalletAdapterClient.unpackExecutionException(e)
            }
            Log.d(TAG, "Signatures: ${result.signatures.contentToString()}")
            signatures = result.signatures
        } catch (e: IOException) {
            Log.e(TAG, "IO error while sending sign_and_send_transaction", e)
        } catch (e: MobileWalletAdapterClient.InvalidPayloadException) {
            Log.e(TAG, "Transaction payload invalid", e)
        } catch (e: MobileWalletAdapterClient.NotCommittedException) {
            Log.e(TAG, "Commitment not reached for all transactions", e)
            signatures = e.signatures
        } catch (e: JsonRpc20Client.JsonRpc20RemoteException) {
            when (e.code) {
                ProtocolContract.ERROR_REAUTHORIZE -> Log.e(TAG, "Reauthorization required", e)
                ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", e)
                ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", e)
                ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", e)
                else -> Log.e(TAG, "Remote exception for authorize", e)
            }
        } catch (e: JsonRpc20Client.JsonRpc20Exception) {
            Log.e(TAG, "JSON-RPC client exception for sign_and_send_transaction", e)
        } catch (e: TimeoutException) {
            Log.e(TAG, "Timed out while waiting for sign_and_send_transaction result", e)
        } catch (e: CancellationException) {
            Log.e(TAG, "sign_and_send_transaction request was cancelled", e)
        }
        return signatures
    }

    private suspend fun <T> localAssociateAndExecute(
        sender: StartActivityForResultSender,
        uriPrefix: Uri? = null,
        action: suspend (MobileWalletAdapterClient) -> T?
    ): T? {
        return mobileWalletAdapterClientMutex.withLock {
            val semConnectedOrFailed = Semaphore(1, 1)
            val semTerminated = Semaphore(1, 1)
            var mobileWalletAdapterClient: MobileWalletAdapterClient? = null
            val scenarioCallbacks = object : Scenario.Callbacks {
                override fun onScenarioReady(client: MobileWalletAdapterClient) {
                    mobileWalletAdapterClient = client
                    semConnectedOrFailed.release()
                }

                override fun onScenarioError() = semConnectedOrFailed.release()
                override fun onScenarioComplete() = semConnectedOrFailed.release()
                override fun onScenarioTeardownComplete() = semTerminated.release()
            }

            val localAssociation = LocalAssociationScenario(
                getApplication<Application>().mainLooper,
                Scenario.DEFAULT_CLIENT_TIMEOUT_MS,
                scenarioCallbacks,
                uriPrefix
            )
            sender.startActivityForResult(localAssociation.createAssociationIntent())

            localAssociation.start()
            try {
                withTimeout(ASSOCIATION_TIMEOUT_MS) {
                    semConnectedOrFailed.acquire()
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Timed out waiting for local association to be ready", e)
                // Let garbage collection deal with cleanup; if we timed out starting, we might
                // hang if we attempt to close.
                return@withLock null
            }

            val result = mobileWalletAdapterClient?.let { client -> action(client) } ?: run {
                Log.e(TAG, "Local association not ready; skip requested action")
                null
            }

            localAssociation.close()
            try {
                withTimeout(ASSOCIATION_TIMEOUT_MS) {
                    semTerminated.acquire()
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Timed out waiting for local association to close", e)
                return@withLock null
            }

            result
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