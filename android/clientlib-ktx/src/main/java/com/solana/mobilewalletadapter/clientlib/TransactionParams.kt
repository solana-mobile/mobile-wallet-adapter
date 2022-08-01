package com.solana.mobilewalletadapter.clientlib

import com.solana.mobilewalletadapter.common.ProtocolContract

sealed class RpcCluster(val name: String) {
    object MainnetBeta : RpcCluster(ProtocolContract.CLUSTER_MAINNET_BETA)
    object Testnet : RpcCluster(ProtocolContract.CLUSTER_TESTNET)
    object Devnet : RpcCluster(ProtocolContract.CLUSTER_DEVNET)
}

open class TransactionParams(
    val minContextSlot: Int?
)

//TODO: We can add other defaults as they become relevant
object DefaultTransactionParams : TransactionParams(
    minContextSlot = null
)
