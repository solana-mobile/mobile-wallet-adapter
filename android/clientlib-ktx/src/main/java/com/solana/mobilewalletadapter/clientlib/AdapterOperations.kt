package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import com.solana.transaction.Transaction

interface AdapterOperations {

    @Deprecated(
        "Replaced by updated authorize() method, which adds MWA 2.0 spec support",
        replaceWith = ReplaceWith("authorize(identityUri, iconUri, identityName, chain, authToken, features, addresses)"),
        DeprecationLevel.WARNING
    )
    suspend fun authorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        rpcCluster: RpcCluster = RpcCluster.MainnetBeta
    ): MobileWalletAdapterClient.AuthorizationResult

    suspend fun authorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        chain: String,
        authToken: String? = null,
        features: Array<String>? = null,
        addresses: Array<ByteArray>? = null,
        signInPayload: SignInWithSolana.Payload? = null
    ): MobileWalletAdapterClient.AuthorizationResult

    suspend fun reauthorize(identityUri: Uri, iconUri: Uri, identityName: String, authToken: String): MobileWalletAdapterClient.AuthorizationResult

    suspend fun deauthorize(authToken: String)

    suspend fun getCapabilities(): MobileWalletAdapterClient.GetCapabilitiesResult

    @Deprecated(
        "Replaced by signMessagesDetached, which returns the improved MobileWalletAdapterClient.SignMessagesResult type",
        replaceWith = ReplaceWith("signMessagesDetached(messages, addresses)"),
        DeprecationLevel.WARNING
    )
    suspend fun signMessages(messages: Array<ByteArray>, addresses: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadsResult

    suspend fun signMessagesDetached(messages: Array<ByteArray>, addresses: Array<ByteArray>): MobileWalletAdapterClient.SignMessagesResult

    suspend fun signTransactions(transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadsResult

    suspend fun signAndSendTransactions(transactions: Array<ByteArray>, params: TransactionParams = DefaultTransactionParams): MobileWalletAdapterClient.SignAndSendTransactionsResult
    suspend fun AdapterOperations.signTransactions(vararg transactions: Transaction): List<Transaction> =
        signTransactions(
            runCatching {
                transactions.map { it.serialize() }.toTypedArray()
            }.getOrElse {
                throw IllegalArgumentException("Transactions could not be serialized", it)
            }
        ).signedPayloads.map { Transaction.from(it) }
    suspend fun AdapterOperations.signAndSendTransactions(vararg transactions: Transaction)
    : MobileWalletAdapterClient.SignAndSendTransactionsResult =
        signAndSendTransactions(
            runCatching {
                transactions.map { it.serialize() }.toTypedArray()
            }.getOrElse {
                throw IllegalArgumentException("Transactions could not be serialized", it)
            }
        )
}