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

    override suspend fun signMessage(authToken: String, transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.signMessage(authToken, transactions)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun signTransaction(authToken: String, transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.signTransaction(authToken, transactions)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun signAndSendTransaction(authToken: String, transactions: Array<ByteArray>, params: TransactionParams): MobileWalletAdapterClient.SignAndSendTransactionResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.signAndSendTransaction(
                authToken,
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