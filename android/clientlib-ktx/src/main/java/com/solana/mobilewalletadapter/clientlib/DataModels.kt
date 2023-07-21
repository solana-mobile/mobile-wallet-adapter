package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult

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
        val authResult: AuthorizationResult? = null
    ): TransactionResult<T>() {

        val safeAuthResult: AuthorizationResult
            get() = authResult!!
    }

    class Failure<T>(
        val message: String,
        val e: Exception
    ): TransactionResult<T>()

    class NoWalletFound<T>(
        val message: String
    ): TransactionResult<T>()
}