package com.solanamobile.ktxclientsample.usecase

import com.portto.solana.web3.Connection
import com.portto.solana.web3.PublicKey
import com.portto.solana.web3.rpc.types.config.Commitment
import com.portto.solana.web3.util.Cluster
import kotlinx.coroutines.*
import javax.inject.Inject

class SolanaRpcUseCase @Inject constructor() {

    private val api by lazy { Connection(Cluster.DEVNET) }

    suspend fun requestAirdrop(pubkey: PublicKey): String =
        withContext(Dispatchers.IO) {
            api.requestAirdrop(pubkey, LAMPORTS_PER_AIRDROP)
        }

    suspend fun awaitConfirmation(signature: String): Deferred<Boolean> {
        return coroutineScope {
            async {
                return@async withContext(Dispatchers.IO) {
                    repeat(10) {
                        api.confirmTransaction(signature, Commitment.CONFIRMED)?.value?.toString() ?: ""
                        return@withContext true
                    }

                    false
                }
            }
        }
    }

    suspend fun getBalance(pubkey: PublicKey): Long =
        withContext(Dispatchers.IO) {
            api.getBalance(pubkey, Commitment.CONFIRMED)
        }

    suspend fun getLatestBlockHash(): String? =
        withContext(Dispatchers.IO) {
            api.getLatestBlockhash(Commitment.FINALIZED)
        }

    companion object {
        const val LAMPORTS_PER_AIRDROP: Long = 100000000
        const val LAMPORTS_PER_SOL: Long = 1000000000
    }
}