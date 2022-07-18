package com.solanamobile.rxclientsample.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.RxMobileWalletAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SampleViewState(
    val isConnected: Boolean = false,
    val userAddress: String = ""
)

@HiltViewModel
class SampleViewModel @Inject constructor(
    private val rxWalletAdapter: RxMobileWalletAdapter
) : ViewModel() {

    private var token = "" //TODO: BAD!

    private val _state = MutableStateFlow(SampleViewState())

    private val compositeDisposable = CompositeDisposable()

    val viewState: StateFlow<SampleViewState>
        get() = _state

    override fun onCleared() {
        compositeDisposable.dispose()
        super.onCleared()
    }

    fun connectToWallet(sender: ActivityResultSender) {
        rxWalletAdapter.transact(sender)
            .subscribe { rxMobileClient ->
                rxMobileClient
                    .authorize(
                        Uri.parse("https://solana.com"), Uri.parse("favicon.ico"), "Solana"
                    )
                    .subscribe { result ->
                        token = result.authToken

                        _state.update {
                            _state.value.copy(
                                isConnected = true,
                                userAddress = result.publicKey
                            )
                        }
                    }.apply { compositeDisposable.add(this) }
            }.apply { compositeDisposable.add(this) }
    }

    fun disconnect(sender: ActivityResultSender) {
        rxWalletAdapter.transact(sender)
            .subscribe { rxMobileClient ->
                rxMobileClient
                    .deauthorize(token)
                    .subscribe {
                        _state.update {
                            _state.value.copy(
                                isConnected = false,
                                userAddress = ""
                            )
                        }
                    }.apply { compositeDisposable.add(this) }
            }.apply { compositeDisposable.add(this) }
    }
}