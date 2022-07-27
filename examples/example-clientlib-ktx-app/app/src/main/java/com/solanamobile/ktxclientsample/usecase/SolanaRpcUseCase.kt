package com.solanamobile.ktxclientsample.usecase

import com.solana.Solana
import com.solana.api.getBalance
import com.solana.api.requestAirdrop
import com.solana.core.PublicKey
import com.solana.networking.NetworkingRouter
import com.solana.networking.RPCEndpoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SolanaRpcUseCase @Inject constructor() {

    private val network = NetworkingRouter(RPCEndpoint.devnetSolana)
    private val solana = Solana(network)

    suspend fun requestAirdrop(pubkey: PublicKey, lamports: Long): String =
        suspendCoroutine { cont ->
            solana.api.requestAirdrop(pubkey, lamports) { result ->
                result.onSuccess {
                    cont.resume(it)
                }

                result.onFailure { throw it }
            }
        }

    suspend fun getBalance(pubkey: PublicKey): Long =
        suspendCoroutine { cont ->
            solana.api.getBalance(pubkey) { result ->
                result.onSuccess {
                    cont.resume(it)
                }

                result.onFailure { throw it }
            }
        }
}