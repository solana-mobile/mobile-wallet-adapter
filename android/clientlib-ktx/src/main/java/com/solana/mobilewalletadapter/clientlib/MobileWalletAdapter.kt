package com.solana.mobilewalletadapter.clientlib

import android.content.ActivityNotFoundException
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

sealed class TransactionResult<T> {
    data class Success<T>(
        val payload: T
    ): TransactionResult<T>()

    class Failure<T>(
        val message: String,
        val e: Exception
    ): TransactionResult<T>()

    class NoWalletFound<T>(
        val message: String
    ): TransactionResult<T>()
}

/**
 * Convenience property to access success payload. Will be null if not successful.
 */
val <T> TransactionResult<T>.successPayload: T?
    get() = (this as? TransactionResult.Success)?.payload

class MobileWalletAdapter(
    private val timeout: Int = Scenario.DEFAULT_CLIENT_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val adapterOperations = LocalAdapterOperations(ioDispatcher)

    suspend fun <T> transact(sender: ActivityResultSender, block: suspend AdapterOperations.() -> T): TransactionResult<T> = coroutineScope {
        return@coroutineScope try {
            val scenario = LocalAssociationScenario(timeout)
            val details = scenario.associationDetails()

            val intent = LocalAssociationIntentCreator.createAssociationIntent(
                details.uriPrefix,
                details.port,
                details.session
            )
            try {
                withTimeout(ASSOCIATION_SEND_INTENT_TIMEOUT_MS) {
                    sender.startActivityForResult(intent) {
                        launch {
                            delay(5000L)
                            scenario.close()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                return@coroutineScope TransactionResult.Failure("Timed out waiting to send association intent", e)
            }

            withContext(ioDispatcher) {
                try {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    val client = scenario.start().get(ASSOCIATION_CONNECT_DISCONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                    adapterOperations.client = client
                    val result = block(adapterOperations)

                    TransactionResult.Success(result)
                } catch (e: InterruptedException) {
                    TransactionResult.Failure("Interrupted while waiting for local association to be ready", e)
                } catch (e: TimeoutException) {
                    TransactionResult.Failure("Timed out waiting for local association to be ready", e)
                } catch (e: ExecutionException) {
                    TransactionResult.Failure("Failed establishing local association with wallet", e)
                } catch (e: CancellationException) {
                    TransactionResult.Failure("Local association was cancelled before connected", e)
                } finally {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    scenario.close().get(ASSOCIATION_CONNECT_DISCONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }
            }
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException -> {
                    return@coroutineScope TransactionResult.Failure("IO error while sending operation", cause)
                }
                is TimeoutException -> {
                    return@coroutineScope TransactionResult.Failure("Timed out while waiting for result", cause)
                }
                is MobileWalletAdapterClient.InvalidPayloadsException -> {
                    return@coroutineScope TransactionResult.Failure("Transaction payloads invalid", cause)
                }
                is MobileWalletAdapterClient.NotSubmittedException -> {
                    return@coroutineScope TransactionResult.Failure("Not all transactions were submitted", cause)
                }
                is JsonRpc20Client.JsonRpc20RemoteException -> {
                    val msg = when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> "Auth token invalid"
                        ProtocolContract.ERROR_NOT_SIGNED -> "User did not authorize signing"
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> "Too many payloads to sign"
                        else -> "Remote exception"
                    }
                    return@coroutineScope TransactionResult.Failure(msg, cause)
                }
                is MobileWalletAdapterClient.InsecureWalletEndpointUriException -> {
                    return@coroutineScope TransactionResult.Failure("Authorization result contained a non-HTTPS wallet base URI", cause)
                }
                is JsonRpc20Client.JsonRpc20Exception -> {
                    return@coroutineScope TransactionResult.Failure("JSON-RPC client exception", cause)
                }
                else -> return@coroutineScope TransactionResult.Failure("Execution exception", e)
            }
        } catch (e: CancellationException) {
            return@coroutineScope TransactionResult.Failure("Request was cancelled", e)
        } catch (e: InterruptedException) {
            return@coroutineScope TransactionResult.Failure("Request was interrupted", e)
        } catch (e: ActivityNotFoundException) {
            return@coroutineScope TransactionResult.NoWalletFound("No compatible wallet found.")
        }
    }

    companion object {
        const val TAG = "MobileWalletAdapter"
        const val ASSOCIATION_SEND_INTENT_TIMEOUT_MS = 20000L
        const val ASSOCIATION_CONNECT_DISCONNECT_TIMEOUT_MS = 10000L
    }
}