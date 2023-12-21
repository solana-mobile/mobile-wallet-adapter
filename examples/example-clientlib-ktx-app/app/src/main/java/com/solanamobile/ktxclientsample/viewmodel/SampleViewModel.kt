package com.solanamobile.ktxclientsample.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.core.PublicKey
import com.solana.core.SerializeConfig
import com.solana.core.Transaction
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult
import com.solana.mobilewalletadapter.clientlib.successPayload
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import com.solana.programs.MemoProgram
import com.solanamobile.ktxclientsample.usecase.Connected
import com.solanamobile.ktxclientsample.usecase.NotConnected
import com.solanamobile.ktxclientsample.usecase.PersistanceUseCase
import com.solanamobile.ktxclientsample.usecase.SolanaRpcUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Base58
import javax.inject.Inject

data class SampleViewState(
    val isLoading: Boolean = false,
    val solBalance: Double = 0.0,
    val userAddress: String = "",
    val userLabel: String = "",
    val memoTx: String = "",
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
                    userAddress = persistedConn.publicKey.toBase58(),
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
                    val publicKey = PublicKey(authResult.accounts.first().publicKey)
                    val blockHash = solanaRpcUseCase.getLatestBlockHash()

                    val tx = Transaction()
                    tx.add(MemoProgram.writeUtf8(publicKey, memoText))
                    tx.setRecentBlockHash(blockHash!!)
                    tx.feePayer = publicKey

                    val bytes = tx.serialize(SerializeConfig(requireAllSignatures = false))
                    val sig = signAndSendTransactions(arrayOf(bytes)).signatures.firstOrNull()
                    Base58.encode(sig)
                }
            }.successPayload?.let { readableSig ->
                _state.value.copy(
                    isLoading = false,
                    memoTx = readableSig
                ).updateViewState()

                // Clear out the recent transaction
                delay(5000)
                _state.value.copy(memoTx = "").updateViewState()
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
                walletAdapter.disconnect(sender)
                persistanceUseCase.clearConnection()
                _state.value.copy(
                    isLoading = false,
                    solBalance = 0.0,
                    userAddress = "",
                    userLabel = ""
                ).updateViewState()
            }
        }
    }

    private suspend fun <T> connect(sender: ActivityResultSender,
                                    block: suspend AdapterOperations.(authResult: AuthorizationResult) -> T): TransactionResult<T> =
        withContext(viewModelScope.coroutineContext) {
            _state.value.copy(
                isLoading = true,
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
                            PublicKey(result.authResult.publicKey),
                            result.authResult.accountLabel ?: "",
                            result.authResult.authToken
                        )

                        persistanceUseCase.persistConnection(currentConn.publicKey, currentConn.accountLabel, currentConn.authToken)

                        _state.value.copy(
                            userAddress = currentConn.publicKey.toBase58(),
                            userLabel = currentConn.accountLabel,
                            solBalance = solanaRpcUseCase.getBalance(currentConn.publicKey)
                        ).updateViewState()
                    }
                    is TransactionResult.NoWalletFound -> {
                        _state.value.copy(
                            walletFound = false
                        ).updateViewState()
                    }
                    is TransactionResult.Failure -> {
                        _state.value.copy(
                            userAddress = "",
                            userLabel = "",
                        ).updateViewState()
                    }
                }
                _state.value.copy(
                    isLoading = false,
                ).updateViewState()
            }
        }

    private suspend fun requestAirdrop(publicKey: PublicKey) {
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