package com.solanamobile.ktxclientsample.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portto.solana.web3.SerializeConfig
import com.portto.solana.web3.Transaction
import com.portto.solana.web3.programs.MemoProgram
import com.solana.mobilewalletadapter.clientlib.*
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
        val connection = persistanceUseCase.getWalletConnection()
        if (connection is Connected) {
            _state.value.copy(
                isLoading = true,
                canTransact = true,
                userAddress = connection.publicKey.toBase58(),
                userLabel = connection.accountLabel,
            ).updateViewState()

            viewModelScope.launch {
                val balance = solanaRpcUseCase.getBalance(connection.publicKey)

                _state.value.copy(
                    isLoading = false,
                    solBalance = balance
                ).updateViewState()
            }
        }
    }

    fun addFunds(sender: ActivityResultSender) {
        viewModelScope.launch {
            val result = walletAdapter.transact(sender) { }

            when (result) {
                is TransactionResult.Success -> {
                    val currentConn = result.payload

                    // result.authResult?.authToken //TODO: Need to think how we want to do this

                    persistanceUseCase.persistConnection(
                        currentConn.publicKey,
                        currentConn.accountLabel,
                        currentConn.authToken
                    )

                    _state.value.copy(
                        isLoading = true
                    ).updateViewState()

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
                            canTransact = true,
                            solBalance = 0.0,
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
                    reauthorize(solanaUri, iconUri, identityName, conn.authToken)
                    signAndSendTransactions(arrayOf(bytes))
                }

                result.successPayload?.signatures?.firstOrNull()?.let { sig ->
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

    fun disconnect(sender: ActivityResultSender) {
        viewModelScope.launch {
            val conn = persistanceUseCase.getWalletConnection()

            if (conn is Connected) {
                walletAdapter.transact(sender) {
                    deauthorize(conn.authToken)
                }

                persistanceUseCase.clearConnection()

                SampleViewState().updateViewState()
            }
        }
    }
}