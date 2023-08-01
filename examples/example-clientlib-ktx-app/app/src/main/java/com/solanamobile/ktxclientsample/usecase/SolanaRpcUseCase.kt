package com.solanamobile.ktxclientsample.usecase

import com.solana.Solana
import com.solana.api.getBalance
import com.solana.api.getConfirmedTransaction
import com.solana.api.requestAirdrop
import com.solana.core.PublicKey
import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.RPCEndpoint
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SolanaRpcUseCase @Inject constructor() {

    private val api by lazy {
        val endPoint = RPCEndpoint.devnetSolana
        val network = HttpNetworkingRouter(endPoint)

        Solana(network).api
    }

    suspend fun requestAirdrop(pubkey: PublicKey): String =
        withContext(Dispatchers.IO) {
//            api.requestAirdrop(pubkey, LAMPORTS_PER_AIRDROP)

            val result = api.requestAirdrop(pubkey, LAMPORTS_PER_AIRDROP)
            result.getOrNull()!!
        }

    suspend fun awaitConfirmationAsync(signature: String): Deferred<Boolean> {
        return coroutineScope {
            async {
                return@async withContext(Dispatchers.IO) {
                    repeat(5) {
                        val result = api.getConfirmedTransaction(signature)
//                        val conf = api.confirmTransaction(signature, Commitment.CONFIRMED)?.value?.toString()

                        if (result.getOrNull() != null) {
                            return@withContext true
                        }
                    }

                    false
                }
            }
        }
    }

    suspend fun getBalance(pubkey: PublicKey, asReadable: Boolean = true): Double =
        withContext(Dispatchers.IO) {
//            val bal = api.getBalance(pubkey, Commitment.CONFIRMED)
            val result = api.getBalance(pubkey)

            if (asReadable) {
                result.getOrNull()!!.toDouble() / LAMPORTS_PER_SOL.toDouble()
            } else {
                result.getOrNull()!!.toDouble()
            }
        }

    suspend fun getLatestBlockHash(): String? =
        withContext(Dispatchers.IO) {
//            api.getLatestBlockhash(Commitment.FINALIZED)
            "TODO"
        }

    companion object {
        const val LAMPORTS_PER_AIRDROP: Long = 100000000
        const val LAMPORTS_PER_SOL: Long = 1000000000
    }
}