package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InvalidObjectException

/**
 * Implementation of local adapter operations, an implementation for the transact block of wallet adapter client
 */
class LocalAdapterOperations(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): AdapterOperations {

    var client: MobileWalletAdapterClient? = null

    override suspend fun authorize(identityUri: Uri, iconUri: Uri, identityName: String): MobileWalletAdapterClient.AuthorizeResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.authorize(identityUri, iconUri, identityName)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun reauthorize(identityUri: Uri, iconUri: Uri, identityName: String, authToken: String): MobileWalletAdapterClient.ReauthorizeResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.reauthorize(identityUri, iconUri, identityName, authToken)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun deauthorize(authToken: String) {
        return withContext(Dispatchers.IO) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.deauthorize(authToken)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun getCapabilities(): MobileWalletAdapterClient.GetCapabilitiesResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.capabilities?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun signMessages(transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadsResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.signMessages(transactions)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun signTransactions(transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadsResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.signTransactions(transactions)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun signAndSendTransactions(transactions: Array<ByteArray>, params: TransactionParams): MobileWalletAdapterClient.SignAndSendTransactionsResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.signAndSendTransactions(
                transactions,
                params.commitmentLevel,
                params.cluster.name,
                params.skipPreflight,
                params.preflightCommitment
            )?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

}