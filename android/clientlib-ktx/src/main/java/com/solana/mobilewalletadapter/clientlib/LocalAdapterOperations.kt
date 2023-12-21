package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
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

    constructor(ioDispatcher: CoroutineDispatcher, client: MobileWalletAdapterClient)
            : this(ioDispatcher) { this.client = client }

    var client: MobileWalletAdapterClient? = null

    @Deprecated(
        "Replaced by updated authorize() method, which adds MWA 2.0 spec support",
        replaceWith = ReplaceWith("authorize(identityUri, iconUri, identityName, chain, authToken, features, addresses)"),
        DeprecationLevel.WARNING
    )
    override suspend fun authorize(identityUri: Uri, iconUri: Uri, identityName: String, rpcCluster: RpcCluster): MobileWalletAdapterClient.AuthorizationResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.authorize(identityUri, iconUri, identityName, rpcCluster.name)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun authorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        chain: String,
        authToken: String?,
        features: Array<String>?,
        addresses: Array<ByteArray>?,
        signInPayload: SignInWithSolana.Payload?
    ): MobileWalletAdapterClient.AuthorizationResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.authorize(identityUri, iconUri, identityName, chain, authToken, features, addresses, signInPayload)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun reauthorize(identityUri: Uri, iconUri: Uri, identityName: String, authToken: String): MobileWalletAdapterClient.AuthorizationResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.reauthorize(identityUri, iconUri, identityName, authToken)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun deauthorize(authToken: String) {
        return withContext(ioDispatcher) {
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

    @Deprecated(
        "Replaced by signMessagesDetached, which returns the improved MobileWalletAdapterClient.SignMessagesResult type",
        replaceWith = ReplaceWith("signMessagesDetached(messages, addresses)"),
        DeprecationLevel.WARNING
    )
    override suspend fun signMessages(messages: Array<ByteArray>, addresses: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadsResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.signMessages(messages, addresses)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    override suspend fun signMessagesDetached(messages: Array<ByteArray>, addresses: Array<ByteArray>): MobileWalletAdapterClient.SignMessagesResult {
        return withContext(ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            client?.signMessagesDetached(messages, addresses)?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

    @Deprecated("signTransactions is deprecated in MWA 2.0, use signAndSendTransactions",
        replaceWith = ReplaceWith("signAndSendTransactions(transactions, DefaultTransactionParams)"),
        DeprecationLevel.WARNING)
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
                params.minContextSlot,
                params.commitment,
                params.skipPreflight,
                params.maxRetries,
                params.waitForCommitmentToSendNextTransaction
            )?.get()
                ?: throw InvalidObjectException("Provide a client before performing adapter operations")
        }
    }

}