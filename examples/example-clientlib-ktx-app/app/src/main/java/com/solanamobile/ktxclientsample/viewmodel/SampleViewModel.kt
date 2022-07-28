package com.solanamobile.ktxclientsample.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portto.solana.web3.PublicKey
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.RpcCluster
import com.solanamobile.ktxclientsample.usecase.Connected
import com.solanamobile.ktxclientsample.usecase.NotConnected
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
            val conn = persistanceUseCase.getWalletConnection()

            val currentConn = walletAdapter.transact(sender) {
                when (conn) {
                   is NotConnected -> {
                       val authed = authorize(solanaUri, iconUri, identityName, RpcCluster.Devnet)
                       Connected(PublicKey(authed.publicKey), authed.authToken)
                   }
                   is Connected -> {
                       val reauthed = reauthorize(solanaUri, iconUri, identityName, conn.authToken)
                       Connected(PublicKey(reauthed.publicKey), reauthed.authToken)
                   }
                }
            }

            persistanceUseCase.persistConnection(currentConn.publickKey, currentConn.authToken)

            _state.value.copy(
                isLoading = true
            ).updateViewState()

            val tx = solanaRpcUseCase.requestAirdrop(currentConn.publickKey)
            val confirmed = solanaRpcUseCase.awaitConfirmationAsync(tx).await()

            if (confirmed) {
                val balance = solanaRpcUseCase.getBalance(currentConn.publickKey)
                val displayBal = balance.toDouble() / SolanaRpcUseCase.LAMPORTS_PER_SOL.toDouble()

                _state.value.copy(
                    isLoading = false,
                    canTransact = true,
                    solBalance = displayBal,
                    userAddress = currentConn.publickKey.toBase58()
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
//                reauthorize(solanaUri, iconUri, identityName, token)
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