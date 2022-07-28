package com.solanamobile.ktxclientsample.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portto.solana.web3.PublicKey
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.RpcCluster
import com.solanamobile.ktxclientsample.usecase.Connected
import com.solanamobile.ktxclientsample.usecase.PersistanceUseCase
import com.solanamobile.ktxclientsample.usecase.SolanaRpcUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SampleViewState(
    val isLoading: Boolean = false,
    val canTransact: Boolean = false,
    val solBalance: Double = 0.0,
    val userAddress: String = ""
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
        val connection = persistanceUseCase.getWalletConnection()
        if (connection is Connected) {
            _state.value.copy(
                isLoading = true,
                canTransact = true,
                userAddress = connection.publickKey.toBase58()
            ).updateViewState()

            viewModelScope.launch {
                val balance = solanaRpcUseCase.getBalance(connection.publickKey)

                _state.value.copy(
                    isLoading = false,
                    solBalance = balance.toDouble() / SolanaRpcUseCase.LAMPORTS_PER_SOL.toDouble()
                ).updateViewState()
            }
        }
    }

    fun addFunds(sender: ActivityResultSender) {
        viewModelScope.launch {
            val result = walletAdapter.transact(sender) {
                authorize(Uri.parse("https://solana.com"), Uri.parse("favicon.ico"), "Solana", RpcCluster.Devnet)
            }

            _state.value.copy(
                isLoading = true
            ).updateViewState()

            val pubkey = PublicKey(result.publicKey)
            persistanceUseCase.persistConnection(pubkey, result.authToken)

            val tx = solanaRpcUseCase.requestAirdrop(pubkey)
            val confirmed = solanaRpcUseCase.awaitConfirmationAsync(tx).await()

            if (confirmed) {
                val balance = solanaRpcUseCase.getBalance(pubkey)
                val displayBal = balance.toDouble() / SolanaRpcUseCase.LAMPORTS_PER_SOL.toDouble()

                _state.value.copy(
                    isLoading = false,
                    canTransact = true,
                    solBalance = displayBal,
                    userAddress = pubkey.toBase58()
                ).updateViewState()
            } else {
                _state.value.copy(
                    isLoading = false,
                    canTransact = true,
                    solBalance = 0.0,
                    userAddress = "Error airdropping"
                ).updateViewState()
            }
        }
    }

    fun publishMemo(sender: ActivityResultSender, memoText: String) {
        _state.value.copy(
            isLoading = true
        ).updateViewState()


        viewModelScope.launch {
//            val pubkey = PublicKey(pubkeyBytes)
//
//            val blockHash = solanaRpcUseCase.getLatestBlockHash()
//
//            val tx = Transaction()
//            tx.add(MemoProgram.writeUtf8(pubkey, memoText))
//            tx.setRecentBlockHash(blockHash!!)
//            tx.feePayer = pubkey
//
//            val bytes = tx.serialize(SerializeConfig(requireAllSignatures = false))
//
//            val result = walletAdapter.transact(sender) {
//                reauthorize(Uri.parse("https://solana.com"), Uri.parse("favicon.ico"), "Solana", token)
//                signAndSendTransactions(arrayOf(bytes))
//            }
//
//            Log.v("Andrew", "Your tx: $result")
//
//            _state.update {
//                _state.value.copy(
//                    isLoading = false
//                )
//            }
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