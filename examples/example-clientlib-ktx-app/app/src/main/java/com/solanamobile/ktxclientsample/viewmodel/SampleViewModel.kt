package com.solanamobile.ktxclientsample.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.core.PublicKey
import com.solana.core.SerializeConfig
import com.solana.core.Transaction
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionCredentials
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
    val canTransact: Boolean = false,
    val solBalance: Double = 0.0,
    val userAddress: String = "",
    val userLabel: String = "",
    val memoTx: String = "",
    val walletFound: Boolean = true
)

val solanaUri = Uri.parse("https://solana.com")
val iconUri = Uri.parse("favicon.ico")
val identityName = "Solana"

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
        var connectionCreds = ConnectionCredentials(solanaUri, iconUri, identityName)
        val persistedConn = persistanceUseCase.getWalletConnection()

        if (persistedConn is Connected) {
            _state.value.copy(
                isLoading = true,
                canTransact = true,
                userAddress = persistedConn.publicKey.toBase58(),
                userLabel = persistedConn.accountLabel,
            ).updateViewState()

            viewModelScope.launch {
                val balance = solanaRpcUseCase.getBalance(persistedConn.publicKey)

                _state.value.copy(
                    isLoading = false,
                    solBalance = balance
                ).updateViewState()
            }

            connectionCreds = connectionCreds.copy(
                authToken = persistedConn.authToken
            )
        }

        walletAdapter.provideCredentials(connectionCreds)
    }

    fun addFunds(sender: ActivityResultSender) {
        viewModelScope.launch {
            val result = walletAdapter.connect(sender)

            when (result) {
                is TransactionResult.Success -> {
                    val currentConn = Connected(
                        PublicKey(result.authResult.publicKey),
                        result.authResult.accountLabel ?: "",
                        result.authResult.authToken
                    )

                    persistanceUseCase.persistConnection(currentConn.publicKey, currentConn.accountLabel, currentConn.authToken)
                    _state.value.copy(
                        isLoading = true,
                        userAddress = currentConn.publicKey.toBase58(),
                        userLabel = currentConn.accountLabel
                    ).updateViewState()

                    try {
                        val tx = solanaRpcUseCase.requestAirdrop(currentConn.publicKey)
                        val confirmed = solanaRpcUseCase.awaitConfirmationAsync(tx).await()

                        if (confirmed) {
                            val balance = solanaRpcUseCase.getBalance(currentConn.publicKey)

                            _state.value.copy(
                                isLoading = false,
                                canTransact = true,
                                solBalance = balance,
                                userAddress = currentConn.publicKey.toBase58(),
                                userLabel = currentConn.accountLabel,
                            ).updateViewState()
                        } else {
                            _state.value.copy(
                                isLoading = false,
                                canTransact = false,
                                userAddress = "Balance not yet confirmed",
                                userLabel = "",
                            ).updateViewState()
                        }
                    } catch (e: Exception) {
                        _state.value.copy(
                            isLoading = false,
                            canTransact = true,
                            userAddress = "Error airdropping",
                            userLabel = "",
                        ).updateViewState()
                    }
                }
                is TransactionResult.NoWalletFound -> {
                    _state.value.copy(
                        walletFound = false
                    ).updateViewState()

                }
                is TransactionResult.Failure -> {
                    Log.v("KTX Sample", result.message)
                }
            }
        }
    }

    fun publishMemo(sender: ActivityResultSender, memoText: String) {
        val conn = persistanceUseCase.getWalletConnection()

        if (conn is Connected) {
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

                (result as? TransactionResult.Success)?.let { txResult ->
                    val updatedAuth = txResult.authResult
                    //TODO: At some point in the future add a method to just persist
                    //just the auth token value as that is all we need in this case
                    persistanceUseCase.persistConnection(PublicKey(updatedAuth.publicKey), updatedAuth.accountLabel ?: "", updatedAuth.authToken)

                    val sig = txResult.payload.signatures.firstOrNull()
                    val readableSig = Base58.encode(sig)

                    _state.value.copy(
                        isLoading = false,
                        memoTx = readableSig
                    ).updateViewState()

                    //Clear out the recent transaction
                    delay(5000)
                    _state.value.copy(memoTx = "").updateViewState()
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val conn = persistanceUseCase.getWalletConnection()
            if (conn is Connected) {
                persistanceUseCase.clearConnection()

                SampleViewState().updateViewState()
            }
        }
    }
}