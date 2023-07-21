package com.solana.mobilewalletadapter.clientlib

import android.net.Uri

sealed class CredentialState {
    data class Provided(
        val credentials: ConnectionCredentials
    ): CredentialState()

    object NotProvided: CredentialState()
}

data class ConnectionCredentials(
    val identityUri: Uri,
    val iconUri: Uri,
    val identityName: String,
    val rpcCluster: RpcCluster = RpcCluster.Devnet,
    val authToken: String? = null
)

sealed class TransactionResult<T> {
    data class Success<T>(
        val payload: T,
        val authToken: String? = null
    ): TransactionResult<T>()

    class Failure<T>(
        val message: String,
        val e: Exception
    ): TransactionResult<T>()

    class NoWalletFound<T>(
        val message: String
    ): TransactionResult<T>()
}