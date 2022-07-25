package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient

interface AdapterOperations {
    suspend fun authorize(identityUri: Uri, iconUri: Uri, identityName: String, rpcCluster: RpcCluster = RpcCluster.MainnetBeta): MobileWalletAdapterClient.AuthorizeResult
    suspend fun reauthorize(identityUri: Uri, iconUri: Uri, identityName: String, authToken: String): MobileWalletAdapterClient.ReauthorizeResult
    suspend fun deauthorize(authToken: String)
    suspend fun getCapabilities(): MobileWalletAdapterClient.GetCapabilitiesResult
    suspend fun signMessages(transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadsResult
    suspend fun signTransactions(transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadsResult
    suspend fun signAndSendTransactions(transactions: Array<ByteArray>, params: TransactionParams = DefaultTransactionParams): MobileWalletAdapterClient.SignAndSendTransactionsResult
}