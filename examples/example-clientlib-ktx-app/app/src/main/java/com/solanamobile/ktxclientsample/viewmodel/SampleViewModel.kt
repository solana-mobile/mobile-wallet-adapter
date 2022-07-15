package com.solanamobile.ktxclientsample.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SampleViewState(
    val isConnected: Boolean = false,
    val userAddress: String = ""
)

@HiltViewModel
class SampleViewModel @Inject constructor(
    private val walletAdapter: MobileWalletAdapter
): ViewModel() {

    private var token = "" //TODO: BAD!

    private val _state = MutableStateFlow(SampleViewState())

    val viewState: StateFlow<SampleViewState>
        get() = _state

    fun connectToWallet(sender: ActivityResultSender) {
        viewModelScope.launch {
            walletAdapter.transact(sender) {
                val result = authorize(Uri.parse("https://solana.com"), Uri.parse("favicon.ico"), "Solana")
                token = result.authToken

                _state.update {
                    _state.value.copy(
                        isConnected = true,
                        userAddress = result.publicKey
                    )
                }
            }
        }
    }

    fun disconnect(sender: ActivityResultSender) {
        viewModelScope.launch {
            walletAdapter.transact(sender) {
                deauthorize(token)

                _state.update {
                    _state.value.copy(
                        isConnected = false,
                        userAddress = ""
                    )
                }
            }
        }
    }
}