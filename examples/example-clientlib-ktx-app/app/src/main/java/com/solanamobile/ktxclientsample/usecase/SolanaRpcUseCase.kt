package com.solanamobile.ktxclientsample.usecase

import com.solana.Solana
import com.solana.api.getBalance
import com.solana.api.getRecentBlockhash
import com.solana.api.requestAirdrop
import com.solana.core.PublicKey
import com.solana.networking.NetworkingRouter
import com.solana.networking.RPCEndpoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SolanaRpcUseCase @Inject constructor() {

    private val network = NetworkingRouter(RPCEndpoint.devnetSolana)
    private val solana = Solana(network)

    suspend fun requestAirdrop(pubkey: PublicKey): String =
        withContext(Dispatchers.IO) {
            suspendCoroutine { cont ->
                solana.api.requestAirdrop(pubkey, LAMPORTS_PER_AIRDROP) { result ->
                    result.onSuccess {
                        cont.resume(it)
                    }

                    result.onFailure { throw it }
                }
            }
        }

    suspend fun awaitConfirmation(signature: String): Deferred<Boolean> {
        return coroutineScope {
            async {
                return@async withContext(Dispatchers.IO) {
                    repeat(10) {
                        val status = getSignatureStatus(signature)
                        if (status == "finalized") {
                            return@withContext true
                        }

                        delay(3000)
                    }

                    false
                }
            }
        }
    }

    suspend fun getSignatureStatus(signature: String): String =
        withContext(Dispatchers.IO) {
            suspendCoroutine { cont ->
                val params = mutableListOf<Any>()
                params.add(listOf(signature))
                params.add(mapOf("searchTransactionHistory" to false))

                solana.api.router.request<Any>("getSignatureStatuses", params, Any::class.javaObjectType) { result ->
                    result.onSuccess {
                        val finalized = "finalized"
                        val rawResult = it.toString()

                        if (rawResult.contains(finalized)) {
                            cont.resume(finalized)
                        } else {
                            cont.resume("pending")
                        }
                    }

                    result.onFailure { throw it }
                }
            }
        }

    suspend fun getBalance(pubkey: PublicKey): Long =
        withContext(Dispatchers.IO) {
            suspendCoroutine { cont ->
                solana.api.getBalance(pubkey) { result ->
                    result.onSuccess {
                        cont.resume(it)
                    }

                    result.onFailure { throw it }
                }
            }
        }

    suspend fun getLatestBlockHash(): String =
        suspendCoroutine { cont ->
            solana.api.getRecentBlockhash { result ->
                result.onSuccess {
                    cont.resume(it)
                }

                result.onFailure { throw it }
            }
        }

    companion object {
        const val LAMPORTS_PER_AIRDROP: Long = 100000000
        const val LAMPORTS_PER_SOL: Long = 1000000000
    }
}