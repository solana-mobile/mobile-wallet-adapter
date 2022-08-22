package com.solanamobile.rxclientsample.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portto.solana.web3.PublicKey
import com.portto.solana.web3.SerializeConfig
import com.portto.solana.web3.Transaction
import com.portto.solana.web3.programs.MemoProgram
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.RxMobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.protocol.RpcCluster
import com.solanamobile.rxclientsample.usecase.Connected
import com.solanamobile.rxclientsample.usecase.NotConnected
import com.solanamobile.rxclientsample.usecase.PersistanceUseCase
import com.solanamobile.rxclientsample.usecase.SolanaRpcUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.disposables.CompositeDisposable
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
    val memoTx: String = ""
)

val solanaUri = Uri.parse("https://solana.com")
val iconUri = Uri.parse("favicon.ico")
val identityName = "Solana"

@HiltViewModel
class SampleViewModel @Inject constructor(
    private val rxWalletAdapter: RxMobileWalletAdapter,
    private val solanaRpcUseCase: SolanaRpcUseCase,
    private val persistanceUseCase: PersistanceUseCase
) : ViewModel() {

    private val compositeDisposable = CompositeDisposable()

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
                userAddress = connection.publicKey.toBase58()
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
        val conn = persistanceUseCase.getWalletConnection()

        rxWalletAdapter.transact(sender)
            .subscribe { rxWalletAdapterClient ->
                val rxAuthorization = when (conn) {
                    is NotConnected -> {
                        rxWalletAdapterClient.authorize(
                            solanaUri,
                            iconUri,
                            identityName,
                            RpcCluster.DEVNET
                        )
                    }
                    is Connected -> {
                        rxWalletAdapterClient.reauthorize(
                            solanaUri,
                            iconUri,
                            identityName,
                            conn.authToken
                        )
                    }
                }
                rxAuthorization
                    .subscribe { authorizationResult ->
                        viewModelScope.launch {
                            val currentConn = Connected(
                                PublicKey(authorizationResult.publicKey),
                                authorizationResult.authToken
                            )
                            persistanceUseCase.persistConnection(
                                currentConn.publicKey,
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
                                    userAddress = currentConn.publicKey.toBase58()
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
                    }.apply { compositeDisposable.add(this) }
            }.apply { compositeDisposable.add(this) }
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

                rxWalletAdapter.transact(sender)
                    .subscribe { rxWalletAdapterClient ->
                        rxWalletAdapterClient.reauthorize(
                            solanaUri,
                            iconUri,
                            identityName,
                            conn.authToken
                        )
                            .subscribe { _ ->
                                rxWalletAdapterClient.signAndSendTransactions(arrayOf(bytes), null)
                                    .subscribe { transactionResult ->
                                        viewModelScope.launch {
                                            transactionResult.signatures.firstOrNull()?.let { sig ->
                                                val readableSig = Base58.encode(sig)

                                                _state.value.copy(
                                                    isLoading = false,
                                                    memoTx = readableSig
                                                ).updateViewState()

                                                // Clear out the recent transaction
                                                delay(5000)
                                                _state.value.copy(memoTx = "").updateViewState()
                                            }
                                        }
                                    }.apply { compositeDisposable.add(this) }
                            }.apply { compositeDisposable.add(this) }
                    }.apply { compositeDisposable.add(this) }
            }
        }
    }

    fun disconnect(sender: ActivityResultSender) {
        viewModelScope.launch {
            val conn = persistanceUseCase.getWalletConnection()
            if (conn is Connected) {
                rxWalletAdapter.transact(sender)
                    .subscribe { rxWalletAdapterClient ->
                        rxWalletAdapterClient.deauthorize(conn.authToken)
                            .subscribe {
                                persistanceUseCase.clearConnection()
                                SampleViewState().updateViewState()
                            }.apply { compositeDisposable.add(this) }
                    }.apply { compositeDisposable.add(this) }
            }
        }
    }
}