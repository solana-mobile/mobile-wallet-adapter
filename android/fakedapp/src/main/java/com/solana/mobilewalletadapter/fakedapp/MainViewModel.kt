/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.GuardedBy
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.whenResumed
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.SignMessagesResult
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.clientlib.transaction.TransactionVersion
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.fakedapp.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    val supportedTxnVersions = listOf(MemoTransactionVersion.Legacy, MemoTransactionVersion.V0)
    private val transactionUseCase get() = when(_uiState.value.txnVersion) {
        MemoTransactionVersion.Legacy -> MemoTransactionLegacyUseCase
        MemoTransactionVersion.V0 -> MemoTransactionV0UseCase
    }

    private val mobileWalletAdapterClientSem = Semaphore(1) // allow only a single MWA connection at a time

    private var isWalletEndpointAvailable = false

    fun checkIsWalletEndpointAvailable() {
        if (!isWalletEndpointAvailable) {
            if (LocalAssociationIntentCreator.isWalletEndpointAvailable(getApplication<Application>().packageManager)) {
                isWalletEndpointAvailable = true
            } else {
                showMessage(R.string.msg_no_wallet_found)
            }
        }
    }

    fun authorize(intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>) = viewModelScope.launch {
        try {
            localAssociateAndExecute(intentLauncher) { client ->
                doAuthorize(client)
            }.await().also { showMessage(R.string.msg_request_succeeded) }
        } catch (e: AssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
        } catch (e: MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking authorize", e)
            showMessage(R.string.msg_request_failed)
        }
    }

    fun reauthorize(intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>) = viewModelScope.launch {
        try {
            localAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doReauthorize(client)
            }.await().also { showMessage(R.string.msg_request_succeeded) }
        } catch (e: AssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
        } catch (e: MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking reauthorize", e)
            showMessage(R.string.msg_request_failed)
        }
    }

    fun deauthorize(intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>) = viewModelScope.launch {
        try {
            localAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doDeauthorize(client)
            }.await().also { showMessage(R.string.msg_request_succeeded) }
        } catch (e: AssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
        } catch (e: MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking deauthorize", e)
            showMessage(R.string.msg_request_failed)
        }
    }

    fun getCapabilities(intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>) = viewModelScope.launch {
        try {
            localAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doGetCapabilities(client)
            }.await().also { showMessage(R.string.msg_request_succeeded) }
        } catch (e: AssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
        } catch (e: MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking get_capabilities", e)
            showMessage(R.string.msg_request_failed)
        }
    }

    fun requestAirdrop() = viewModelScope.launch {
        try {
            RequestAirdropUseCase(CLUSTER_RPC_URI, _uiState.value.publicKey!!)
            Log.d(TAG, "Airdrop request sent")
            showMessage(R.string.msg_airdrop_request_sent)
        } catch (e: RequestAirdropUseCase.AirdropFailedException) {
            Log.e(TAG, "Airdrop request failed", e)
            showMessage(R.string.msg_airdrop_failed)
        }
    }

    fun setTransactionVersion(txnVersion: MemoTransactionVersion) {
        _uiState.update { it.copy(txnVersion = txnVersion) }
    }

    fun signTransactions(intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>, numTransactions: Int) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO) {
            GetLatestBlockhashUseCase(CLUSTER_RPC_URI)
        }

        val signedTransactions = try {
            localAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doReauthorize(client)
                val (blockhash, _) = latestBlockhash.await()
                val transactions = Array(numTransactions) {
                    transactionUseCase.create(uiState.value.publicKey!!, blockhash)
                }
                doSignTransactions(client, transactions)
            }.await()
        } catch (e: AssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking reauthorize + sign_transactions", e)
            showMessage(R.string.msg_request_failed)
            return@launch
        } catch (e: GetLatestBlockhashUseCase.GetLatestBlockhashFailedException) {
            Log.e(TAG, "Failed retrieving latest blockhash", e)
            showMessage(R.string.msg_request_failed)
            return@launch
        }

        val verified = signedTransactions.map { txn ->
            try {
                transactionUseCase.verify(uiState.value.publicKey!!, txn)
                true
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Memo transaction signature verification failed", e)
                false
            }
        }
        showMessage(if (verified.all { it }) R.string.msg_request_succeeded else R.string.msg_signature_verification_failed)
    }

    fun authorizeAndSignTransactions(intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO) {
            GetLatestBlockhashUseCase(CLUSTER_RPC_URI)
        }

        val signedTransactions = try {
            localAssociateAndExecute(intentLauncher) { client ->
                doAuthorize(client)
                val (blockhash, _) = latestBlockhash.await()
                val transactions = Array(1) {
                    transactionUseCase.create(uiState.value.publicKey!!, blockhash)
                }
                doSignTransactions(client, transactions)
            }.await()
        } catch (e: AssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking authorize + sign_transactions", e)
            showMessage(R.string.msg_request_failed)
            return@launch
        } catch (e: GetLatestBlockhashUseCase.GetLatestBlockhashFailedException) {
            Log.e(TAG, "Failed retrieving latest blockhash", e)
            showMessage(R.string.msg_request_failed)
            return@launch
        }

        val verified = signedTransactions.map { txn ->
            try {
                transactionUseCase.verify(uiState.value.publicKey!!, txn)
                true
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Memo transaction signature verification failed", e)
                false
            }
        }

        showMessage(if (verified.all { it }) R.string.msg_request_succeeded else R.string.msg_signature_verification_failed)
    }

    fun authorizeAndSignMessageAndSignTransaction(intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO, CoroutineStart.LAZY) {
            GetLatestBlockhashUseCase(CLUSTER_RPC_URI)
        }

        lateinit var message: ByteArray
        val (signedMessage: SignMessagesResult.SignedMessage, transactionSignature: ByteArray) = try {
            localAssociateAndExecute(intentLauncher) { client ->
                doAuthorize(client)

                message =
                    "Sign this message to prove you own account ${Base58EncodeUseCase(uiState.value.publicKey!!)}".encodeToByteArray()
                val signMessagesResult = doSignMessages(client, arrayOf(message), arrayOf(uiState.value.publicKey!!))

                Log.d(TAG, "Simulating a short delay while we do something with the message the user just signed...")
                latestBlockhash.start() // Kick off fetching the blockhash before we delay, to reduce latency
                delay(1500) // Simulate a 1.5-second wait while we do something with the signed message

                val (blockhash, slot) = latestBlockhash.await()
                val transaction =
                    arrayOf(transactionUseCase.create(uiState.value.publicKey!!, blockhash))
                val signAndSendTransactionsResult = doSignAndSendTransactions(client, transaction, slot)

                signMessagesResult[0] to signAndSendTransactionsResult[0]
            }.await()
        } catch (e: AssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking authorize + sign_messages + sign_and_send_transactions", e)
            showMessage(R.string.msg_request_failed)
            return@launch
        } catch (e: GetLatestBlockhashUseCase.GetLatestBlockhashFailedException) {
            Log.e(TAG, "Failed retrieving latest blockhash", e)
            showMessage(R.string.msg_request_failed)
            return@launch
        }

        val messageSignatureVerified = try {
            OffChainMessageSigningUseCase.verify(
                signedMessage.message,
                signedMessage.signatures[0],
                uiState.value.publicKey!!,
                message
            )
            true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed verifying signature on message", e)
            false
        }

        Log.d(TAG, "Transaction signature(base58)= ${Base58EncodeUseCase(transactionSignature)}")

        showMessage(
            if (messageSignatureVerified) R.string.msg_request_succeeded
            else R.string.msg_signature_verification_failed
        )
    }

    fun signMessages(intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>, numMessages: Int) = viewModelScope.launch {
        val messages = Array(numMessages) { i ->
            when (i) {
                1 -> ByteArray(1232) { j -> ('a' + (j % 10)).code.toByte() }
                2 -> ByteArray(32768) { j -> j.toByte() }
                else -> "A simple test message $i".encodeToByteArray()
            }
        }
        val signedMessages = try {
            localAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doReauthorize(client)
                doSignMessages(client, messages, arrayOf(_uiState.value.publicKey!!))
            }.await()
        } catch (e: AssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking reauthorize + sign_transactions", e)
            showMessage(R.string.msg_request_failed)
            return@launch
        }

        try {
            for (sm in signedMessages.zip(messages)) {
                Log.d(TAG, "Verifying signature of $sm")
                OffChainMessageSigningUseCase.verify(
                    sm.first.message,
                    sm.first.signatures[0],
                    _uiState.value.publicKey!!,
                    sm.second
                )
            }
            showMessage(R.string.msg_request_succeeded)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed verifying signature on message", e)
            showMessage(R.string.msg_request_failed)
        }
    }

    fun signAndSendTransactions(intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>, numTransactions: Int) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO) {
            GetLatestBlockhashUseCase(CLUSTER_RPC_URI)
        }

        try {
            localAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doReauthorize(client)
                val (blockhash, slot) = latestBlockhash.await()
                val transactions = Array(numTransactions) {
                    transactionUseCase.create(uiState.value.publicKey!!, blockhash)
                }
                doSignAndSendTransactions(client, transactions, slot)
            }.await().also { showMessage(R.string.msg_request_succeeded) }
        } catch (e: AssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking reauthorize + sign_and_send_transactions", e)
            showMessage(R.string.msg_request_failed)
            return@launch
        } catch (e: GetLatestBlockhashUseCase.GetLatestBlockhashFailedException) {
            Log.e(TAG, "Failed retrieving latest blockhash", e)
            showMessage(R.string.msg_request_failed)
            return@launch
        }
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

    private suspend fun doAuthorize(client: MobileWalletAdapterClient) = coroutineScope {
        try {
            val result = runInterruptible(Dispatchers.IO) {
                client.authorize(
                    Uri.parse("https://solana.com"),
                    Uri.parse("favicon.ico"),
                    "Solana",
                    CLUSTER
                ).get()
            }
            Log.d(TAG, "Authorized: $result")
            _uiState.update {
                it.copy(
                    authToken = result.authToken,
                    publicKey = result.publicKey,
                    accountLabel = result.accountLabel,
                    walletUriBase = result.walletUriBase
                )
            }
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException ->
                    throw MobileWalletAdapterOperationFailedException("IO error while sending authorize", cause)
                is TimeoutException ->
                    throw MobileWalletAdapterOperationFailedException("Timed out while waiting for authorize result", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED ->
                            throw MobileWalletAdapterOperationFailedException("Not authorized", cause)
                        ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED ->
                            throw MobileWalletAdapterOperationFailedException("Cluster not supported", cause)
                        else ->
                            throw MobileWalletAdapterOperationFailedException("Remote exception for authorize", cause)
                    }
                is MobileWalletAdapterClient.InsecureWalletEndpointUriException ->
                    throw MobileWalletAdapterOperationFailedException("authorize result contained a non-HTTPS wallet base URI", cause)
                is JsonRpc20Client.JsonRpc20Exception ->
                    throw MobileWalletAdapterOperationFailedException("JSON-RPC client exception for authorize", cause)
                else -> throw MobileWalletAdapterOperationFailedException(null, e)
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "authorize request was cancelled", e)
            throw e
        }
    }

    private suspend fun doReauthorize(client: MobileWalletAdapterClient) = coroutineScope {
        try {
            val result = runInterruptible(Dispatchers.IO) {
                client.reauthorize(
                    Uri.parse("https://solana.com"),
                    Uri.parse("favicon.ico"),
                    "Solana",
                    _uiState.value.authToken!!
                ).get()
            }
            Log.d(TAG, "Reauthorized: $result")
            _uiState.update {
                it.copy(
                    authToken = result.authToken,
                    publicKey = result.publicKey,
                    walletUriBase = result.walletUriBase
                )
            }
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException ->
                    throw MobileWalletAdapterOperationFailedException("IO error while sending reauthorize", cause)
                is TimeoutException ->
                    throw MobileWalletAdapterOperationFailedException("Timed out while waiting for reauthorize result", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> {
                            _uiState.update {
                                it.copy(
                                    authToken = null,
                                    publicKey = null,
                                    walletUriBase = null
                                )
                            }
                            throw MobileWalletAdapterOperationFailedException("Not reauthorized", cause)
                        }
                        else ->
                            throw MobileWalletAdapterOperationFailedException("Remote exception for reauthorize", cause)
                    }
                is MobileWalletAdapterClient.InsecureWalletEndpointUriException ->
                    throw MobileWalletAdapterOperationFailedException("reauthorize result contained a non-HTTPS wallet base URI", cause)
                is JsonRpc20Client.JsonRpc20Exception ->
                    throw MobileWalletAdapterOperationFailedException("JSON-RPC client exception for reauthorize", cause)
                else -> throw MobileWalletAdapterOperationFailedException(null, e)
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "reauthorize request was cancelled", e)
            throw e
        }
    }

    private suspend fun doDeauthorize(client: MobileWalletAdapterClient) = coroutineScope {
        try {
            runInterruptible(Dispatchers.IO) {
                client.deauthorize(_uiState.value.authToken!!).get()
            }
            Log.d(TAG, "Deauthorized")
            _uiState.update { it.copy(authToken = null, publicKey = null, walletUriBase = null) }
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException ->
                    throw MobileWalletAdapterOperationFailedException("IO error while sending deauthorize", cause)
                is TimeoutException ->
                    throw MobileWalletAdapterOperationFailedException("Timed out while waiting for deauthorize result", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    throw MobileWalletAdapterOperationFailedException("Remote exception for deauthorize", cause)
                is JsonRpc20Client.JsonRpc20Exception ->
                    throw MobileWalletAdapterOperationFailedException("JSON-RPC client exception for deauthorize", cause)
                else -> throw MobileWalletAdapterOperationFailedException(null, e)
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "deauthorize request was cancelled", e)
            throw e
        }
    }

    private suspend fun doGetCapabilities(
        client: MobileWalletAdapterClient
    ): MobileWalletAdapterClient.GetCapabilitiesResult = coroutineScope {
        try {
            val result = runInterruptible(Dispatchers.IO) { client.getCapabilities().get() }
            Log.d(TAG, "Capabilities: $result")
            Log.d(TAG, "Supports legacy transactions: ${TransactionVersion.supportsLegacy(result.supportedTransactionVersions)}")
            Log.d(TAG, "Supports v0 transactions: ${TransactionVersion.supportsVersion(result.supportedTransactionVersions, 0)}")
            result
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException ->
                    throw MobileWalletAdapterOperationFailedException("IO error while sending get_capabilities", cause)
                is TimeoutException ->
                    throw MobileWalletAdapterOperationFailedException("Timed out while waiting for get_capabilities result", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    throw MobileWalletAdapterOperationFailedException("Remote exception for get_capabilities", cause)
                is JsonRpc20Client.JsonRpc20Exception ->
                    throw MobileWalletAdapterOperationFailedException("JSON-RPC client exception for get_capabilities", cause)
                else -> throw MobileWalletAdapterOperationFailedException(null, e)
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "get_capabilities request was cancelled", e)
            throw e
        }
    }

    private suspend fun doSignTransactions(
        client: MobileWalletAdapterClient,
        transactions: Array<ByteArray>
    ): Array<ByteArray> = coroutineScope {
        try {
            val result =
                runInterruptible(Dispatchers.IO) { client.signTransactions(transactions).get() }
            Log.d(TAG, "Signed transaction(s): $result")
            result.signedPayloads
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException ->
                    throw MobileWalletAdapterOperationFailedException("IO error while sending sign_transactions", cause)
                is TimeoutException ->
                    throw MobileWalletAdapterOperationFailedException("Timed out while waiting for sign_transactions result", cause)
                is MobileWalletAdapterClient.InvalidPayloadsException ->
                    throw MobileWalletAdapterOperationFailedException("Transaction payloads invalid", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED ->
                            throw MobileWalletAdapterOperationFailedException("Authorization invalid, authorization or reauthorization required", cause)
                        ProtocolContract.ERROR_NOT_SIGNED ->
                            throw MobileWalletAdapterOperationFailedException("User did not authorize signing", cause)
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS ->
                            throw MobileWalletAdapterOperationFailedException("Too many payloads to sign", cause)
                        else ->
                            throw MobileWalletAdapterOperationFailedException("Remote exception for sign_transactions", cause)
                    }
                is JsonRpc20Client.JsonRpc20Exception ->
                    throw MobileWalletAdapterOperationFailedException("JSON-RPC client exception for sign_transactions", cause)
                else -> throw MobileWalletAdapterOperationFailedException(null, e)
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "sign_transactions request was cancelled", e)
            throw e
        }
    }

    private suspend fun doSignMessages(
        client: MobileWalletAdapterClient,
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>
    ): Array<SignMessagesResult.SignedMessage> = coroutineScope {
        try {
            val result = runInterruptible(Dispatchers.IO) {
                client.signMessagesDetached(messages, addresses).get()
            }
            Log.d(TAG, "Signed message(s): $result")
            result.messages
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException ->
                    throw MobileWalletAdapterOperationFailedException("IO error while sending sign_messages", cause)
                is TimeoutException ->
                    throw MobileWalletAdapterOperationFailedException("Timed out while waiting for sign_messages result", cause)
                is MobileWalletAdapterClient.InvalidPayloadsException ->
                    throw MobileWalletAdapterOperationFailedException("Message payloads invalid", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED ->
                            throw MobileWalletAdapterOperationFailedException("Authorization invalid, authorization or reauthorization required", cause)
                        ProtocolContract.ERROR_NOT_SIGNED ->
                            throw MobileWalletAdapterOperationFailedException("User did not authorize signing", cause)
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS ->
                            throw MobileWalletAdapterOperationFailedException("Too many payloads to sign", cause)
                        else ->
                            throw MobileWalletAdapterOperationFailedException("Remote exception for sign_messages", cause)
                    }
                is JsonRpc20Client.JsonRpc20Exception ->
                    throw MobileWalletAdapterOperationFailedException("JSON-RPC client exception for sign_messages", cause)
                else -> throw MobileWalletAdapterOperationFailedException(null, e)
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "sign_messages request was cancelled", e)
            throw e
        }
    }

    private suspend fun doSignAndSendTransactions(
        client: MobileWalletAdapterClient,
        transactions: Array<ByteArray>,
        minContextSlot: Int? = null
    ): Array<ByteArray> = coroutineScope {
        try {
            val result = runInterruptible(Dispatchers.IO) {
                client.signAndSendTransactions(transactions, minContextSlot).get()
            }
            Log.d(TAG, "Signatures: ${result.signatures.contentToString()}")
            result.signatures
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException ->
                    throw MobileWalletAdapterOperationFailedException("IO error while sending sign_and_send_transactions", cause)
                is TimeoutException ->
                    throw MobileWalletAdapterOperationFailedException("Timed out while waiting for sign_and_send_transactions result", cause)
                is MobileWalletAdapterClient.InvalidPayloadsException ->
                    throw MobileWalletAdapterOperationFailedException("Transaction payloads invalid", cause)
                is MobileWalletAdapterClient.NotSubmittedException ->
                    throw MobileWalletAdapterOperationFailedException("Not all transactions were submitted", cause)
                is JsonRpc20Client.JsonRpc20RemoteException ->
                    when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED ->
                            throw MobileWalletAdapterOperationFailedException("Authorization invalid, authorization or reauthorization required", cause)
                        ProtocolContract.ERROR_NOT_SIGNED ->
                            throw MobileWalletAdapterOperationFailedException("User did not authorize signing", cause)
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS ->
                            throw MobileWalletAdapterOperationFailedException("Too many payloads to sign", cause)
                        else ->
                            throw MobileWalletAdapterOperationFailedException("Remote exception for sign_and_send_transactions", cause)
                    }
                is JsonRpc20Client.JsonRpc20Exception ->
                    throw MobileWalletAdapterOperationFailedException("JSON-RPC client exception for sign_and_send_transactions", cause)
                else -> throw MobileWalletAdapterOperationFailedException(null, e)
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "sign_and_send_transactions request was cancelled", e)
            throw e
        }
    }

    private suspend fun <T> localAssociateAndExecute(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>,
        uriPrefix: Uri? = null,
        action: suspend (MobileWalletAdapterClient) -> T
    ): Deferred<T> = coroutineScope {
        // Use async to launch in a new Job, for proper cancellation semantics
        async {
            mobileWalletAdapterClientSem.withPermit {
                val contract = intentLauncher.contract as StartMobileWalletAdapterActivity
                val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)

                val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
                    uriPrefix,
                    localAssociation.port,
                    localAssociation.session
                )
                try {
                    contract.waitForActivityResumed() // may throw TimeoutCancellationException
                } catch (e: TimeoutCancellationException) {
                    throw AssociationFailedException("Timed out waiting to start Mobile Wallet Adapter Activity", e)
                }
                try {
                    intentLauncher.launch(StartMobileWalletAdapterActivity.CreateParams(associationIntent, this))
                } catch (e: ActivityNotFoundException) {
                    showMessage(R.string.msg_no_wallet_found)
                    throw AssociationFailedException("No Mobile Wallet Adapter Activity available", e)
                }

                withContext(Dispatchers.IO) {
                    try {
                        val mobileWalletAdapterClient = try {
                            runInterruptible {
                                localAssociation.start().get(LOCAL_ASSOCIATION_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            }
                        } catch (e: TimeoutException) {
                            throw AssociationFailedException("Timed out waiting for local association to be ready", e)
                        } catch (e: ExecutionException) {
                            throw AssociationFailedException("Failed establishing local association with wallet", e.cause)
                        }

                        contract.onMobileWalletAdapterClientConnected(this)

                        action(mobileWalletAdapterClient)
                    } finally {
                        @Suppress("BlockingMethodInNonBlockingContext") // running in Dispatchers.IO; blocking is appropriate
                        localAssociation.close().get(LOCAL_ASSOCIATION_CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    }
                }
            }
        }
    }

    // Note: do not hold an instance of this class in a member field of the ViewModel. It contains a
    // reference back to the Activity (by way of activityLifecycle). It should only be consumed
    // transiently, as part of invoking localAssociateAndExecute.
    class StartMobileWalletAdapterActivity(private val activityLifecycle: Lifecycle) :
        ActivityResultContract<StartMobileWalletAdapterActivity.CreateParams, ActivityResult>() {
        data class CreateParams(val intent: Intent, val coroutineScope: CoroutineScope)

        @GuardedBy("this")
        private var scope: CoroutineScope? = null
        @GuardedBy("this")
        private var connected: Boolean = false

        override fun createIntent(
            context: Context, input: CreateParams
        ): Intent {
            synchronized(this) {
                scope = input.coroutineScope
                connected = false
            }
            return input.intent
        }

        override fun parseResult(resultCode: Int, intent: Intent?): ActivityResult {
            val scope: CoroutineScope?
            val connected: Boolean
            synchronized(this) {
                scope = this.scope.also { this.scope = null }
                connected = this.connected.also { this.connected = false }
            }

            scope?.let {
                if (connected) {
                    // If the Mobile Wallet Adapter connection was ever established, allow time
                    // for it to terminate gracefully before cancelling the containing Job. This
                    // scope may have already terminated, in which case the Job created by launch
                    // will immediately move to the CANCELED state itself.
                    it.launch {
                        delay(LOCAL_ASSOCIATION_CANCEL_AFTER_WALLET_CLOSED_TIMEOUT_MS)
                        cancel()
                    }
                } else {
                    it.cancel() // No-op if the scope is already cancelled
                }
            }
            return ActivityResult(resultCode, intent)
        }

        internal suspend fun waitForActivityResumed() = coroutineScope {
            withTimeout(LOCAL_ASSOCIATION_SEND_INTENT_TIMEOUT_MS) {
                activityLifecycle.whenResumed {}
            }
        }

        internal fun onMobileWalletAdapterClientConnected(scope: CoroutineScope) {
            synchronized(this) {
                this.scope = scope
                this.connected = true
            }
        }
    }

    sealed class LocalAssociateAndExecuteException(message: String?, cause: Throwable?) :
        Exception(message, cause)

    class AssociationFailedException(message: String?, cause: Throwable?) :
        LocalAssociateAndExecuteException(message, cause)

    class MobileWalletAdapterOperationFailedException(message: String?, cause: Throwable?) :
        LocalAssociateAndExecuteException(message, cause)

    data class UiState(
        val authToken: String? = null,
        val publicKey: ByteArray? = null, // TODO(#44): support multiple addresses
        val accountLabel: String? = null,
        val walletUriBase: Uri? = null,
        val messages: List<String> = emptyList(),
        val txnVersion: MemoTransactionVersion = MemoTransactionVersion.Legacy
    ) {
        val hasAuthToken: Boolean get() = (authToken != null)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UiState

            if (authToken != other.authToken) return false
            if (publicKey != null) {
                if (other.publicKey == null) return false
                if (!publicKey.contentEquals(other.publicKey)) return false
            } else if (other.publicKey != null) return false
            if (walletUriBase != other.walletUriBase) return false
            if (messages != other.messages) return false
            if (txnVersion != other.txnVersion) return false

            return true
        }

        override fun hashCode(): Int {
            var result = authToken?.hashCode() ?: 0
            result = 31 * result + (publicKey?.contentHashCode() ?: 0)
            result = 31 * result + (walletUriBase?.hashCode() ?: 0)
            result = 31 * result + messages.hashCode()
            result = 31 * result + txnVersion.hashCode()
            return result
        }
    }

    companion object {
        private val TAG = MainViewModel::class.simpleName
        private const val LOCAL_ASSOCIATION_SEND_INTENT_TIMEOUT_MS = 20000L
        private const val LOCAL_ASSOCIATION_START_TIMEOUT_MS = 60000L // LocalAssociationScenario.start() has a shorter timeout; this is just a backup safety measure
        private const val LOCAL_ASSOCIATION_CLOSE_TIMEOUT_MS = 2000L
        private const val LOCAL_ASSOCIATION_CANCEL_AFTER_WALLET_CLOSED_TIMEOUT_MS = 5000L
        private val CLUSTER_RPC_URI = Uri.parse("https://api.testnet.solana.com")
        private val CLUSTER = ProtocolContract.CLUSTER_TESTNET
    }
}