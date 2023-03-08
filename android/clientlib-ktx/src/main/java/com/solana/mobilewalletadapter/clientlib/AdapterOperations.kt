package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient

interface AdapterOperations {
    suspend fun authorize(identityUri: Uri, iconUri: Uri, identityName: String, rpcCluster: RpcCluster = RpcCluster.MainnetBeta): MobileWalletAdapterClient.AuthorizationResult
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
}