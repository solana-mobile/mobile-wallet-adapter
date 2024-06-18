package com.solanamobile.ktxclientsample.usecase

import com.solana.networking.KtorNetworkDriver
import com.solana.publickey.SolanaPublicKey
import com.solana.rpc.Commitment
import com.solana.rpc.SolanaRpcClient
import com.solana.rpc.TransactionOptions
import com.solanamobile.ktxclientsample.BuildConfig
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SolanaRpcUseCase @Inject constructor() {

    private val rpc: SolanaRpcClient

    init {
        val url = BuildConfig.HELIUS_KEY?.let {
            "https://devnet.helius-rpc.com/?api-key=$it"
        } ?: "https://api.devnet.solana.com"

        rpc = SolanaRpcClient(url, KtorNetworkDriver())
    }

    suspend fun requestAirdrop(pubkey: SolanaPublicKey): String =
        withContext(Dispatchers.IO) {
            val result = rpc.requestAirdrop(pubkey,
                LAMPORTS_PER_AIRDROP/LAMPORTS_PER_SOL.toFloat())
            result.result ?: throw Error(result.error?.message)
        }

    suspend fun awaitConfirmationAsync(signature: String): Deferred<Boolean> {
        return coroutineScope {
            async {
                return@async withContext(Dispatchers.IO) {
                    rpc.confirmTransaction(signature, TransactionOptions(commitment = Commitment.CONFIRMED))
                        .getOrDefault(false)
                }
            }
        }
    }

    suspend fun getBalance(pubkey: SolanaPublicKey, asReadable: Boolean = true): Double =
        withContext(Dispatchers.IO) {
            val result = rpc.getBalance(pubkey).let { result ->
                result.result ?: throw Error(result.error?.message)
            }

            if (asReadable) {
                result.toDouble() / LAMPORTS_PER_SOL.toDouble()
            } else {
                result.toDouble()
            }
        }

    suspend fun getLatestBlockHash(): String =
        withContext(Dispatchers.IO) {
            rpc.getLatestBlockhash().run {
                result?.blockhash ?: throw Error(error?.message)
            }
        }

    companion object {
        const val LAMPORTS_PER_AIRDROP: Long = 100000000
        const val LAMPORTS_PER_SOL: Long = 1000000000
    }
}