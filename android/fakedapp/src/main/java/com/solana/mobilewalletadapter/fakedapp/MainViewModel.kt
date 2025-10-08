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
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult.AuthorizedAccount
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult.SignInResult
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.transaction.TransactionVersion
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.SessionProperties.ProtocolVersion
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
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
                doAuthorize(client, IDENTITY, CHAIN_ID)
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

    fun signInWithSolana(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>
    ) = viewModelScope.launch {
        try {
            val signInPayload = SignInWithSolana.Payload(
                IDENTITY.uri?.host,
                "Sign into Fake dApp to do fake things!"
            )

            doLocalAssociateAndExecute(intentLauncher) { client ->
                doSignIn(client, IDENTITY, CHAIN_ID, signInPayload)
            }.also {
                Log.d(TAG, "Signed In: $it")
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
                Log.d(TAG, "Supported features: ${it.supportedOptionalFeatures.contentToString()}")
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
            RequestAirdropUseCase(CLUSTER_RPC_URI, _uiState.value.primaryPublicKey!!)
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

    fun setSelectedAccount(accountIndex: Int) {
        _uiState.update { it.copy(selectedAccount = _uiState.value.accounts?.get(accountIndex)!!) }
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
                    transactionUseCase.create(uiState.value.primaryPublicKey!!, blockhash)
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
                transactionUseCase.verify(uiState.value.primaryPublicKey!!, txn)
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
                doAuthorize(client, IDENTITY, CHAIN_ID).also {
                    Log.d(TAG, "Authorized: $it")
                }
                val (blockhash, _) = latestBlockhash.await()
                val transactions = arrayOf(
                    transactionUseCase.create(uiState.value.primaryPublicKey!!, blockhash)
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
                transactionUseCase.verify(uiState.value.primaryPublicKey!!, txn)
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
                doAuthorize(client, IDENTITY, CHAIN_ID)

                message =
                    "Sign this message to prove you own account ${Base58EncodeUseCase(uiState.value.primaryPublicKey!!)}".encodeToByteArray()
                val signMessagesResult = client.signMessagesDetached(
                    arrayOf(message),
                    arrayOf(uiState.value.primaryPublicKey!!)
                )

                Log.d(TAG, "Simulating a short delay while we do something with the message the user just signed...")
                latestBlockhash.start() // Kick off fetching the blockhash before we delay, to reduce latency
                delay(1500) // Simulate a 1.5-second wait while we do something with the signed message

                val (blockhash, slot) = latestBlockhash.await()
                val transaction =
                    arrayOf(transactionUseCase.create(uiState.value.primaryPublicKey!!, blockhash))
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
                uiState.value.primaryPublicKey!!,
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
                client.signMessagesDetached(messages,
                    arrayOf(_uiState.value.selectedAccount!!.publicKey)).also {
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
                    _uiState.value.selectedAccount!!.publicKey,
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
                    transactionUseCase.create(uiState.value.primaryPublicKey!!, blockhash)
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
        chain: String?
    ): MobileWalletAdapterClient.AuthorizationResult {
        val result = try {
            client.authorize(identity, chain, null,
                uiState.value.accounts?.map { it.publicKey },
                uiState.value.sessionProtocolVersion!!)
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
            _uiState.update {
                it.copy(
                    authToken = null,
                    accounts = null,
                    walletUriBase = null
                )
            }
            throw e
        }

        _uiState.update {
            it.copy(
                authToken = result.authToken,
                accounts = result.accounts.asList(),
                selectedAccount = result.accounts.first(),
                walletUriBase = result.walletUriBase
            )
        }

        return result
    }

    private suspend fun doSignIn(
        client: MobileWalletAdapterUseCase.Client,
        identity: MobileWalletAdapterUseCase.DappIdentity,
        chain: String?,
        signInPayload: SignInWithSolana.Payload
    ): MobileWalletAdapterClient.AuthorizationResult {
        val result = try {
            val authResult = client.authorize(identity, chain, signInPayload,
                uiState.value.accounts?.map { it.publicKey },
                uiState.value.sessionProtocolVersion!!)

            val signInResult = authResult.signInResult ?: run {
                Log.i(TAG, "Sign in failed, no sign in result returned from wallet, falling back on sign message")
                val publicKey = authResult.accounts.first().publicKey
                val signInMessage = signInPayload.prepareMessage(publicKey)
                client.signMessagesDetached(
                    arrayOf(signInMessage.encodeToByteArray()),
                    arrayOf(publicKey)
                ).first().let {
                    SignInResult(it.addresses.first(), it.message,
                        it.signatures.first(), "ed25519")
                }
            }

            val expectedMessage = signInPayload.prepareMessage(signInResult.publicKey).encodeToByteArray()
            if (!expectedMessage.contentEquals(signInResult.signedMessage)) {
                throw MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException(
                    "signed message does not match expected SIWS payload",
                    IllegalArgumentException("signed message does not match expected SIWS payload")
                )
            }

            Log.d(TAG, "Verifying signature of $signInResult")
            OffChainMessageSigningUseCase.verify(
                signInResult.signedMessage,
                signInResult.signature,
                signInResult.publicKey,
                expectedMessage
            )

            authResult
        } catch (e: MobileWalletAdapterUseCase.MobileWalletAdapterOperationFailedException) {
            _uiState.update {
                it.copy(
                    authToken = null,
                    accounts = null,
                    walletUriBase = null
                )
            }
            throw e
        }

        _uiState.update {
            it.copy(
                authToken = result.authToken,
                accounts = result.accounts.asList(),
                selectedAccount = result.accounts.first(),
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
                    accounts = null,
                    walletUriBase = null
                )
            }
            throw e
        }

        _uiState.update {
            it.copy(
                authToken = result.authToken,
                accounts = result.accounts.asList(),
                selectedAccount = it.selectedAccount ?: result.accounts.first(),
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
                    accounts = null,
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
            MobileWalletAdapterUseCase.localAssociateAndExecute(intentLauncher, uriPrefix) { client, sessionProperties ->
                _uiState.update {
                    it.copy(sessionProtocolVersion = sessionProperties.protocolVersion)
                }
                action(client)
            }
        } catch (e: MobileWalletAdapterUseCase.NoWalletAvailableException) {
            showMessage(R.string.msg_no_wallet_found)
            throw e
        }
    }

    data class UiState(
        val authToken: String? = null,
        val accounts: List<AuthorizedAccount>? = null,
        val selectedAccount: AuthorizedAccount? = null,
        val walletUriBase: Uri? = null,
        val messages: List<String> = emptyList(),
        val txnVersion: MemoTransactionVersion = MemoTransactionVersion.Legacy,
        val sessionProtocolVersion: ProtocolVersion? = null
    ) {
        val hasAuthToken: Boolean get() = (authToken != null)
        val primaryPublicKey: ByteArray? get() = (accounts?.first()?.publicKey)
        val accountLabels: List<String>? get() = accounts?.map { it.accountLabel ?: Base58EncodeUseCase(it.publicKey) }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UiState

            if (authToken != other.authToken) return false
            if (accounts != null && accounts.size == other.accounts?.size) {
                accounts.zip(other.accounts).all { (a1, a2) -> a1.publicKey contentEquals a2.publicKey }
            } else if (other.accounts != null) return false
            if (!(selectedAccount?.publicKey contentEquals other.selectedAccount?.publicKey)) return false
            if (walletUriBase != other.walletUriBase) return false
            if (messages != other.messages) return false
            if (txnVersion != other.txnVersion) return false
            if (sessionProtocolVersion != other.sessionProtocolVersion) return false

            return true
        }

        override fun hashCode(): Int {
            var result = authToken?.hashCode() ?: 0
            result = 31 * result + (accounts?.hashCode() ?: 0)
            result = 31 * result + (selectedAccount?.hashCode() ?: 0)
            result = 31 * result + (walletUriBase?.hashCode() ?: 0)
            result = 31 * result + messages.hashCode()
            result = 31 * result + txnVersion.hashCode()
            result = 31 * result + (sessionProtocolVersion?.hashCode() ?: 0)
            return result
        }
    }

    companion object {
        private val TAG = MainViewModel::class.simpleName
        private val CLUSTER_RPC_URI = Uri.parse("https://api.testnet.solana.com")
        private const val CHAIN_ID = ProtocolContract.CHAIN_SOLANA_TESTNET
        private val IDENTITY = MobileWalletAdapterUseCase.DappIdentity(
            uri = Uri.parse("https://solanamobile.com"),
            iconRelativeUri = Uri.parse("favicon.ico"),
            name = "FakeDApp"
        )
        private const val MAX_MESSAGE_SIZE = 1232 // max size of a type 0 or 1 off-chain message + header (https://docs.solana.com/cli/sign-offchain-message)
    }
}
