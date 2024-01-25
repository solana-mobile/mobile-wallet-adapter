package com.solana.mobilewalletadapter.clientlib

import com.solana.mobilewalletadapter.common.ProtocolContract

@Deprecated(
    "RpcCluster is deprecated as of MWA 2.0",
    replaceWith = ReplaceWith("Use the Blockchain object for multi-chain support"),
    DeprecationLevel.WARNING
)
sealed class RpcCluster(val name: String) {
    object MainnetBeta : RpcCluster(ProtocolContract.CLUSTER_MAINNET_BETA)
    object Testnet : RpcCluster(ProtocolContract.CLUSTER_TESTNET)
    object Devnet : RpcCluster(ProtocolContract.CLUSTER_DEVNET)
    class Custom(name: String) : RpcCluster(name)
}

sealed class Blockchain(
    val name: String,
    val cluster: String
) {
    val fullName
        get() = "$name:$cluster"

    @Deprecated(
        "RpcCluster is deprecated as of MWA 2.0, this property is preserved for backwards compatibility with legacy endpoints",
        level = DeprecationLevel.WARNING
    )
    val rpcCluster: RpcCluster = when (cluster) {
        "mainnet", ProtocolContract.CLUSTER_MAINNET_BETA -> RpcCluster.MainnetBeta
        ProtocolContract.CLUSTER_TESTNET -> RpcCluster.Testnet
        ProtocolContract.CLUSTER_DEVNET -> RpcCluster.Devnet
        else -> RpcCluster.Custom(cluster)
    }
}

sealed class Solana {
    object Mainnet: Blockchain("solana", "mainnet")
    object Testnet: Blockchain("solana", ProtocolContract.CLUSTER_TESTNET)
    object Devnet: Blockchain("solana", ProtocolContract.CLUSTER_DEVNET)
}

open class TransactionParams(
    val minContextSlot: Int?,
    val commitment: String?,
    val skipPreflight: Boolean?,
    val maxRetries: Int?,
    val waitForCommitmentToSendNextTransaction: Boolean?
)

//TODO: We can add other defaults as they become relevant
object DefaultTransactionParams : TransactionParams(
    minContextSlot = null,
    commitment = null,
    skipPreflight = null,
    maxRetries = null,
    waitForCommitmentToSendNextTransaction = null
)
