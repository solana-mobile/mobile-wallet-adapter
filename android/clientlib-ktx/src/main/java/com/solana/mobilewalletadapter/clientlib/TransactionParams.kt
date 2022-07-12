package com.solana.mobilewalletadapter.clientlib

import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel

sealed class RpcCluster(val name: String) {
    object MainnetBeta : RpcCluster(ProtocolContract.CLUSTER_MAINNET_BETA)
    object Testnet : RpcCluster(ProtocolContract.CLUSTER_TESTNET)
    object Devnet : RpcCluster(ProtocolContract.CLUSTER_DEVNET)
}

open class TransactionParams(
    val commitmentLevel: CommitmentLevel,
    val cluster: RpcCluster,
    val skipPreflight: Boolean,
    val preflightCommitment: CommitmentLevel?
)

object DefaultTestnet : TransactionParams(
    commitmentLevel = CommitmentLevel.Confirmed,
    cluster = RpcCluster.Testnet,
    skipPreflight = false,
    preflightCommitment = null
)
