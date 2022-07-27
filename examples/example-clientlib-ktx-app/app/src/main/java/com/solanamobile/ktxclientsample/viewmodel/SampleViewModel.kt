package com.solanamobile.ktxclientsample.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.core.PublicKey
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solanamobile.ktxclientsample.usecase.SolanaRpcUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SampleViewState(
    val canTransact: Boolean = false,
    val solBalance: Double = 0.0,
    val userAddress: String = ""
)

@HiltViewModel
class SampleViewModel @Inject constructor(
    private val walletAdapter: MobileWalletAdapter,
    private val solanaRpcUseCase: SolanaRpcUseCase
): ViewModel() {

    private val _state = MutableStateFlow(SampleViewState())

    val viewState: StateFlow<SampleViewState>
        get() = _state

    fun addFunds(sender: ActivityResultSender) {
        viewModelScope.launch {
            val result = walletAdapter.transact(sender) {
                authorize(Uri.parse("https://solana.com"), Uri.parse("favicon.ico"), "Solana")
            }

            val pubkey = PublicKey(result.publicKey)

            val tx = solanaRpcUseCase.requestAirdrop(pubkey)
            val confirmed = solanaRpcUseCase.awaitConfirmation(tx).await()

            if (confirmed) {
                val balance = solanaRpcUseCase.getBalance(pubkey)
                val displayBal = balance.toDouble() / SolanaRpcUseCase.LAMPORTS_PER_SOL.toDouble()

                _state.update {
                    _state.value.copy(
                        canTransact = true,
                        solBalance = displayBal,
                        userAddress = pubkey.toBase58()
                    )
                }
            } else {
                _state.update {
                    _state.value.copy(
                        canTransact = true,
                        solBalance = 0.0,
                        userAddress = "Error airdropping"
                    )
                }
            }
        }
    }

    fun disconnect(sender: ActivityResultSender) {
        viewModelScope.launch {
            walletAdapter.transact(sender) {
                //deauthorize("")
            }
        }
    }
}