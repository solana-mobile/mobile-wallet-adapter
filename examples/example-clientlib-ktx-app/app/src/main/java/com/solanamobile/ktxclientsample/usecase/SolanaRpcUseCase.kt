package com.solanamobile.ktxclientsample.usecase

import com.solana.Solana
import com.solana.api.Api
import com.solana.api.getBalance
import com.solana.api.getRecentBlockhash
import com.solana.api.getSignatureStatuses
import com.solana.api.requestAirdrop
import com.solana.core.PublicKey
import com.solana.models.SignatureStatusRequestConfiguration
import com.solana.networking.Commitment
import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.RPCEndpoint
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SolanaRpcUseCase @Inject constructor() {

    private val api: Api

    init {
        val endPoint = RPCEndpoint.devnetSolana
        val network = HttpNetworkingRouter(endPoint)

        api = Solana(network).api
    }

    suspend fun requestAirdrop(pubkey: PublicKey): String =
        withContext(Dispatchers.IO) {
            val result = api.requestAirdrop(pubkey, LAMPORTS_PER_AIRDROP)
            result.getOrThrow()
        }

    suspend fun awaitConfirmationAsync(signature: String): Deferred<Boolean> {
        return coroutineScope {
            async {
                return@async withContext(Dispatchers.IO) {
                    repeat(5) {
                        val result = api.getSignatureStatuses(listOf(signature), SignatureStatusRequestConfiguration(true))
                        val status = result.getOrThrow()[0].confirmationStatus

                        if (status == Commitment.CONFIRMED.value || status == Commitment.FINALIZED.value) {
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
            val result = api.getBalance(pubkey)

            if (asReadable) {
                result.getOrThrow().toDouble() / LAMPORTS_PER_SOL.toDouble()
            } else {
                result.getOrThrow().toDouble()
            }
        }

    suspend fun getLatestBlockHash(): String =
        withContext(Dispatchers.IO) {
            api.getRecentBlockhash().getOrThrow()
        }

    companion object {
        const val LAMPORTS_PER_AIRDROP: Long = 100000000
        const val LAMPORTS_PER_SOL: Long = 1000000000
    }
}