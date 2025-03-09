package com.solanamobile.ktxclientsample.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult
import com.solana.mobilewalletadapter.clientlib.successPayload
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import com.solana.programs.MemoProgram
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.toUnsignedTransaction
import com.solanamobile.ktxclientsample.usecase.Connected
import com.solanamobile.ktxclientsample.usecase.NotConnected
import com.solanamobile.ktxclientsample.usecase.PersistanceUseCase
import com.solanamobile.ktxclientsample.usecase.SolanaRpcUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bitcoinj.base.Base58
import javax.inject.Inject

data class SampleViewState(
    val isLoading: Boolean = false,
    val solBalance: Double = 0.0,
    val userAddress: String = "",
    val userLabel: String = "",
    val memoTx: String = "",
    val error: String = "",
    val walletFound: Boolean = true
)

@HiltViewModel
class SampleViewModel @Inject constructor(
    private val walletAdapter: MobileWalletAdapter,
    private val solanaRpcUseCase: SolanaRpcUseCase,
    private val persistanceUseCase: PersistanceUseCase
): ViewModel() {

    private fun SampleViewState.updateViewState() {
        _state.update { this }
    }

    private val _state = MutableStateFlow(SampleViewState())

    val viewState: StateFlow<SampleViewState>
        get() = _state

    fun loadConnection() {
        val persistedConn = persistanceUseCase.getWalletConnection()

        if (persistedConn is Connected) {
            viewModelScope.launch {
                _state.value.copy(
                    userAddress = persistedConn.publicKey.base58(),
                    userLabel = persistedConn.accountLabel,
                    solBalance = solanaRpcUseCase.getBalance(persistedConn.publicKey)
                ).updateViewState()
            }

            walletAdapter.authToken = persistedConn.authToken
        }
    }

    fun addFunds() {
        check(persistanceUseCase.getWalletConnection() is Connected) {
            "Cannot add funds, no wallet connected"
        }
        viewModelScope.launch {
            requestAirdrop(persistanceUseCase.connected.publicKey)
        }
    }

    fun publishMemo(sender: ActivityResultSender, memoText: String) {
        viewModelScope.launch {
            connect(sender) { authResult ->
                withContext(Dispatchers.IO) {
                    val publicKey = SolanaPublicKey(authResult.accounts.first().publicKey)
                    val blockHash = solanaRpcUseCase.getLatestBlockHash()

                    val tx = Message.Builder()
                        .setRecentBlockhash(blockHash)
                        .addInstruction(MemoProgram.publishMemo(publicKey, memoText))
                        .build().toUnsignedTransaction()

                    val bytes = tx.serialize()
                    val sig = signAndSendTransactions(arrayOf(bytes)).signatures.firstOrNull()
                    Base58.encode(sig)
                }
            }.successPayload?.let { readableSig ->
                _state.value.copy(
                    isLoading = false,
                    memoTx = readableSig
                ).updateViewState()
            }
        }
    }

    fun signIn(sender: ActivityResultSender) {
        viewModelScope.launch {
            connect(sender) {}
            // Note: should check the signature here of the signInResult to verify it matches the
            // account and expected signed message. This is left as an exercise for the reader.
        }
    }

    fun disconnect(sender: ActivityResultSender) {
        val conn = persistanceUseCase.getWalletConnection()
        if (conn is Connected) {
            viewModelScope.launch {
                persistanceUseCase.clearConnection()
                walletAdapter.disconnect(sender)
                _state.value.copy(
                    isLoading = false,
                    solBalance = 0.0,
                    userAddress = "",
                    userLabel = "",
                    memoTx = "",
                    error = ""
                ).updateViewState()
            }
        }
    }

    private suspend fun <T> connect(sender: ActivityResultSender,
                                    block: suspend AdapterOperations.(authResult: AuthorizationResult) -> T): TransactionResult<T> =
        withContext(viewModelScope.coroutineContext) {
            _state.value.copy(
                isLoading = true,
                memoTx = "",
                error = ""
            ).updateViewState()
            val conn = persistanceUseCase.getWalletConnection()
            return@withContext walletAdapter.transact(sender,
                if (conn is NotConnected) SignInWithSolana.Payload("solana.com",
                    "Sign in to Ktx Sample App") else null) {
                block(it)
            }.also { result ->
                when (result) {
                    is TransactionResult.Success -> {
                        val currentConn = Connected(
                            SolanaPublicKey(result.authResult.accounts.first().publicKey),
                            result.authResult.accounts.first().accountLabel ?: "",
                            result.authResult.authToken,
                            result.authResult.walletUriBase
                        )

                        persistanceUseCase.persistConnection(currentConn.publicKey,
                            currentConn.accountLabel, currentConn.authToken, currentConn.walletUriBase)

                        _state.value.copy(
                            userAddress = currentConn.publicKey.base58(),
                            userLabel = currentConn.accountLabel,
                            solBalance = solanaRpcUseCase.getBalance(currentConn.publicKey)
                        ).updateViewState()
                    }
                    is TransactionResult.NoWalletFound -> {
                        _state.value.copy(
                            walletFound = false,
                            error = result.message
                        ).updateViewState()
                    }
                    is TransactionResult.Failure -> {
                        _state.value.copy(
                            error = result.message
                        ).updateViewState()
                    }
                }
                _state.value.copy(
                    isLoading = false,
                ).updateViewState()
            }
        }

    private suspend fun requestAirdrop(publicKey: SolanaPublicKey) {
        try {
            val tx = solanaRpcUseCase.requestAirdrop(publicKey)
            val confirmed = solanaRpcUseCase.awaitConfirmationAsync(tx).await()

            if (confirmed) {
                _state.value.copy(
                    isLoading = false,
                    solBalance = solanaRpcUseCase.getBalance(publicKey)
                ).updateViewState()
            }
        } catch (e: Throwable) {
            _state.value.copy(
                userAddress = "Error airdropping",
                userLabel = "",
            ).updateViewState()
        }

        _state.value.copy(isLoading = false).updateViewState()
    }
}