/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.transaction.TransactionVersion
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.fakedapp.usecase.*
import com.solana.mobilewalletadapter.fakedapp.usecase.MobileWalletAdapterUseCase.StartMobileWalletAdapterActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    val supportedTxnVersions = listOf(MemoTransactionVersion.Legacy, MemoTransactionVersion.V0)
    private val transactionUseCase get() = when(_uiState.value.txnVersion) {
        MemoTransactionVersion.Legacy -> MemoTransactionLegacyUseCase
        MemoTransactionVersion.V0 -> MemoTransactionV0UseCase
    }

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

    fun authorize(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>
    ) = viewModelScope.launch {
        try {
            doLocalAssociateAndExecute(intentLauncher) { client ->
                doAuthorize(client, IDENTITY, CLUSTER_NAME)
            }.also {
                Log.d(TAG, "Authorized: $it")
                showMessage(R.string.msg_request_succeeded)
            }
        } catch (e: MobileWalletAdapterUseCase.LocalAssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking authorize", e)
            showMessage(R.string.msg_request_failed)
        }
    }

    fun reauthorize(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>
    ) = viewModelScope.launch {
        try {
            doLocalAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doReauthorize(client, IDENTITY, _uiState.value.authToken!!)
            }.also {
                Log.d(TAG, "Reauthorized: $it")
                showMessage(R.string.msg_request_succeeded)
            }
        } catch (e: MobileWalletAdapterUseCase.LocalAssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking reauthorize", e)
            showMessage(R.string.msg_request_failed)
        }
    }

    fun deauthorize(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>
    ) = viewModelScope.launch {
        try {
            doLocalAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doDeauthorize(client, _uiState.value.authToken!!)
            }.also {
                Log.d(TAG, "Deauthorized")
                showMessage(R.string.msg_request_succeeded)
            }
        } catch (e: MobileWalletAdapterUseCase.LocalAssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
            Log.e(TAG, "Failed invoking deauthorize", e)
            showMessage(R.string.msg_request_failed)
        }
    }

    fun getCapabilities(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>
    ) = viewModelScope.launch {
        try {
            doLocalAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                client.getCapabilities()
            }.also {
                Log.d(TAG, "Capabilities: $it")
                Log.d(TAG, "Supports legacy transactions: ${TransactionVersion.supportsLegacy(it.supportedTransactionVersions)}")
                Log.d(TAG, "Supports v0 transactions: ${TransactionVersion.supportsVersion(it.supportedTransactionVersions, 0)}")
                showMessage(R.string.msg_request_succeeded)
            }
        } catch (e: MobileWalletAdapterUseCase.LocalAssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
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

    fun signTransactions(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>,
        numTransactions: Int
    ) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO) {
            GetLatestBlockhashUseCase(CLUSTER_RPC_URI)
        }

        val signedTransactions = try {
            doLocalAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doReauthorize(client, IDENTITY, _uiState.value.authToken!!).also {
                    Log.d(TAG, "Reauthorized: $it")
                }
                val (blockhash, _) = latestBlockhash.await()
                val transactions = Array(numTransactions) {
                    transactionUseCase.create(uiState.value.publicKey!!, blockhash)
                }
                client.signTransactions(transactions).also {
                    Log.d(TAG, "Signed transaction(s): $it")
                }
            }
        } catch (e: MobileWalletAdapterUseCase.LocalAssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
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

    fun authorizeAndSignTransactions(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>
    ) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO) {
            GetLatestBlockhashUseCase(CLUSTER_RPC_URI)
        }

        val signedTransactions = try {
            doLocalAssociateAndExecute(intentLauncher) { client ->
                doAuthorize(client, IDENTITY, CLUSTER_NAME).also {
                    Log.d(TAG, "Authorized: $it")
                }
                val (blockhash, _) = latestBlockhash.await()
                val transactions = arrayOf(
                    transactionUseCase.create(uiState.value.publicKey!!, blockhash)
                )
                client.signTransactions(transactions).also {
                    Log.d(TAG, "Signed transaction(s): $it")
                }
            }
        } catch (e: MobileWalletAdapterUseCase.LocalAssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
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

    fun authorizeAndSignMessageAndSignTransaction(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>
    ) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO, CoroutineStart.LAZY) {
            GetLatestBlockhashUseCase(CLUSTER_RPC_URI)
        }

        lateinit var message: ByteArray
        val (
            signedMessage: MobileWalletAdapterClient.SignMessagesResult.SignedMessage,
            transactionSignature: ByteArray
        ) = try {
            doLocalAssociateAndExecute(intentLauncher) { client ->
                doAuthorize(client, IDENTITY, CLUSTER_NAME)

                message =
                    "Sign this message to prove you own account ${Base58EncodeUseCase(uiState.value.publicKey!!)}".encodeToByteArray()
                val signMessagesResult = client.signMessagesDetached(
                    arrayOf(message),
                    arrayOf(uiState.value.publicKey!!)
                )

                Log.d(TAG, "Simulating a short delay while we do something with the message the user just signed...")
                latestBlockhash.start() // Kick off fetching the blockhash before we delay, to reduce latency
                delay(1500) // Simulate a 1.5-second wait while we do something with the signed message

                val (blockhash, slot) = latestBlockhash.await()
                val transaction =
                    arrayOf(transactionUseCase.create(uiState.value.publicKey!!, blockhash))
                val signAndSendTransactionsResult = client.signAndSendTransactions(transaction, slot)

                signMessagesResult[0] to signAndSendTransactionsResult[0]
            }
        } catch (e: MobileWalletAdapterUseCase.LocalAssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
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

    fun signMessages(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>,
        numMessages: Int
    ) = viewModelScope.launch {
        val messages = Array(numMessages) { i ->
            when (i) {
                1 -> ByteArray(MAX_MESSAGE_SIZE) { j -> ('a' + (j % 10)).code.toByte() }
                2 -> ByteArray(MAX_MESSAGE_SIZE) { j -> j.toByte() }
                else -> "A simple test message $i".encodeToByteArray()
            }
        }
        val signedMessages = try {
            doLocalAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doReauthorize(client, IDENTITY, _uiState.value.authToken!!).also {
                    Log.d(TAG, "Reauthorized: $it")
                }
                client.signMessagesDetached(messages, arrayOf(_uiState.value.publicKey!!)).also {
                    Log.d(TAG, "Signed message(s): $it")
                }
            }
        } catch (e: MobileWalletAdapterUseCase.LocalAssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
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

    fun signAndSendTransactions(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>,
        numTransactions: Int
    ) = viewModelScope.launch {
        val latestBlockhash = viewModelScope.async(Dispatchers.IO) {
            GetLatestBlockhashUseCase(CLUSTER_RPC_URI)
        }

        try {
            doLocalAssociateAndExecute(intentLauncher, _uiState.value.walletUriBase) { client ->
                doReauthorize(client, IDENTITY, _uiState.value.authToken!!).also {
                    Log.d(TAG, "Reauthorized: $it")
                }
                val (blockhash, slot) = latestBlockhash.await()
                val transactions = Array(numTransactions) {
                    transactionUseCase.create(uiState.value.publicKey!!, blockhash)
                }
                client.signAndSendTransactions(transactions, slot).also {
                    Log.d(TAG, "Transaction signature(s): $it")
                }
            }.also { showMessage(R.string.msg_request_succeeded) }
        } catch (e: MobileWalletAdapterUseCase.LocalAssociationFailedException) {
            Log.e(TAG, "Error associating", e)
            showMessage(R.string.msg_association_failed)
            return@launch
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
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

    private suspend fun doAuthorize(
        client: MobileWalletAdapterUseCase.Client,
        identity: MobileWalletAdapterUseCase.DappIdentity,
        cluster: String?
    ): MobileWalletAdapterClient.AuthorizationResult {
        val result = try {
            client.authorize(identity, cluster)
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
            _uiState.update {
                it.copy(
                    authToken = null,
                    publicKey = null,
                    accountLabel = null,
                    walletUriBase = null
                )
            }
            throw e
        }

        _uiState.update {
            it.copy(
                authToken = result.authToken,
                publicKey = result.publicKey,
                accountLabel = result.accountLabel,
                walletUriBase = result.walletUriBase
            )
        }

        return result
    }

    private suspend fun doReauthorize(
        client: MobileWalletAdapterUseCase.Client,
        identity: MobileWalletAdapterUseCase.DappIdentity,
        currentAuthToken: String
    ): MobileWalletAdapterClient.AuthorizationResult {
        val result = try {
            client.reauthorize(identity, currentAuthToken)
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
            _uiState.update {
                it.copy(
                    authToken = null,
                    publicKey = null,
                    accountLabel = null,
                    walletUriBase = null
                )
            }
            throw e
        }

        _uiState.update {
            it.copy(
                authToken = result.authToken,
                publicKey = result.publicKey,
                accountLabel = result.accountLabel,
                walletUriBase = result.walletUriBase
            )
        }

        return result
    }

    private suspend fun doDeauthorize(
        client: MobileWalletAdapterUseCase.Client,
        currentAuthToken: String
    ) {
        try {
            client.deauthorize(currentAuthToken)
        } finally {
            _uiState.update {
                it.copy(
                    authToken = null,
                    publicKey = null,
                    accountLabel = null,
                    walletUriBase = null
                )
            }
        }
    }

    private suspend fun <T> doLocalAssociateAndExecute(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>,
        uriPrefix: Uri? = null,
        action: suspend (MobileWalletAdapterUseCase.Client) -> T
    ): T {
        return try {
            MobileWalletAdapterUseCase.localAssociateAndExecute(intentLauncher, uriPrefix, action)
        } catch (e: MobileWalletAdapterUseCase.NoWalletAvailableException) {
            showMessage(R.string.msg_no_wallet_found)
            throw e
        }
    }

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
        private val CLUSTER_RPC_URI = Uri.parse("https://api.testnet.solana.com")
        private const val CLUSTER_NAME = ProtocolContract.CLUSTER_TESTNET
        private val IDENTITY = MobileWalletAdapterUseCase.DappIdentity(
            uri = Uri.parse("https://solanamobile.com"),
            iconRelativeUri = Uri.parse("favicon.ico"),
            name = "FakeDApp"
        )
        private const val MAX_MESSAGE_SIZE = 1232 // max size of a type 0 or 1 off-chain message + header (https://docs.solana.com/cli/sign-offchain-message)
    }
}