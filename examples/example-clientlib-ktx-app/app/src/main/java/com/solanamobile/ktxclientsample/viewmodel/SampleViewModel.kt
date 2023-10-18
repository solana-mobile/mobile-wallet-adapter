package com.solanamobile.ktxclientsample.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.core.PublicKey
import com.solana.core.SerializeConfig
import com.solana.core.Transaction
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.programs.MemoProgram
import com.solanamobile.ktxclientsample.usecase.Connected
import com.solanamobile.ktxclientsample.usecase.PersistanceUseCase
import com.solanamobile.ktxclientsample.usecase.SolanaRpcUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    fun addFunds(sender: ActivityResultSender) {
        viewModelScope.launch {
            if (connectIfNeeded(sender)) {
                val conn = persistanceUseCase.connected

                requestAirdrop(conn.publicKey)
            }
        }
    }

    fun publishMemo(sender: ActivityResultSender, memoText: String) {
        viewModelScope.launch {
            if (connectIfNeeded(sender)) {
                val conn = persistanceUseCase.connected

                _state.value.copy(
                    isLoading = true
                ).updateViewState()

                viewModelScope.launch {
                    val blockHash = solanaRpcUseCase.getLatestBlockHash()

                    val tx = Transaction()
                    tx.add(MemoProgram.writeUtf8(conn.publicKey, memoText))
                    tx.setRecentBlockHash(blockHash!!)
                    tx.feePayer = conn.publicKey

                    val bytes = tx.serialize(SerializeConfig(requireAllSignatures = false))
                    val result = walletAdapter.transact(sender) {
                        signAndSendTransactions(arrayOf(bytes))
                    }

                    when (result) {
                        is TransactionResult.Success -> {
                            val updatedAuth = result.authResult
                            //TODO: At some point in the future add a method to just persist
                            //just the auth token value as that is all we need in this case
                            persistanceUseCase.persistConnection(
                                PublicKey(updatedAuth.publicKey),
                                updatedAuth.accountLabel ?: "",
                                updatedAuth.authToken
                            )

                            val sig = result.payload.signatures.firstOrNull()
                            val readableSig = Base58.encode(sig)

                            _state.value.copy(
                                isLoading = false,
                                memoTx = readableSig
                            ).updateViewState()

                            //Clear out the recent transaction
                            delay(5000)
                            _state.value.copy(memoTx = "").updateViewState()
                        }
                        else -> {
                            _state.value.copy(
                                isLoading = false,
                            ).updateViewState()
                        }
                    }
                }
            }
        }
    }

    private suspend fun connectIfNeeded(sender: ActivityResultSender): Boolean {
        val conn = persistanceUseCase.getWalletConnection()

        return if (conn is Connected) {
            true
        } else {
            when (val result = walletAdapter.connect(sender)) {
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

                    true
                }
                is TransactionResult.NoWalletFound -> {
                    _state.value.copy(
                        walletFound = false
                    ).updateViewState()

                    false
                }
                is TransactionResult.Failure -> {
                    _state.value.copy(
                        userAddress = "",
                        userLabel = "",
                    ).updateViewState()

                    false
                }
            }
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