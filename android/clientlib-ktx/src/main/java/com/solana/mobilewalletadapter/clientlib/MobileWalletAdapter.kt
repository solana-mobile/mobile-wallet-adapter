package com.solana.mobilewalletadapter.clientlib

import android.util.Log
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

class MobileWalletAdapter(
    private val timeout: Int = Scenario.DEFAULT_CLIENT_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val adapterOperations = LocalAdapterOperations(ioDispatcher)

    suspend fun <T> transact(sender: ActivityResultSender, block: suspend AdapterOperations.() -> T): T = coroutineScope {
        return@coroutineScope try {
            val scenario = LocalAssociationScenario(timeout)
            val details = scenario.associationDetails()

            val intent = LocalAssociationIntentCreator.createAssociationIntent(details.uriPrefix, details.port, details.session)
            sender.startActivityForResult(intent) {
                launch {
                    delay(5000L)

                    Log.v("Andrew", "Cancelling")
                    this@coroutineScope.cancel()
                }
            }

            withContext(Dispatchers.IO) {
                val result = try {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    val client = scenario.start().get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                    adapterOperations.client = client
                    block(adapterOperations)
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Interrupted while waiting for local association to be ready")
                    throw e
                } catch (e: TimeoutException) {
                    Log.e(TAG, "Timed out waiting for local association to be ready")
                    throw e
                } catch (e: ExecutionException) {
                    Log.e(TAG, "Failed establishing local association with wallet", e.cause)
                    throw e
                } catch (e: CancellationException) {
                    Log.e(TAG, "Local association was cancelled before connected", e)
                    throw e
                } finally {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    scenario.close().get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }

                result
            }
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is IOException -> {
                    Log.e(TAG, "IO error while sending operation", cause)
                    throw e
                }
                is TimeoutException -> {
                    Log.e(TAG, "Timed out while waiting for result", cause)
                    throw e
                }
                is MobileWalletAdapterClient.InvalidPayloadsException -> {
                    Log.e(TAG, "Transaction payloads invalid", cause)
                    throw e
                }
                is MobileWalletAdapterClient.NotSubmittedException -> {
                    Log.e(TAG, "Not all transactions were submitted", cause)
                    throw e
                }
                is JsonRpc20Client.JsonRpc20RemoteException -> {
                    when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", cause)
                        ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", cause)
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", cause)
                        else -> Log.e(TAG, "Remote exception", cause)
                    }
                    throw e
                }
                is MobileWalletAdapterClient.InsecureWalletEndpointUriException -> {
                    Log.e(TAG, "Authorization result contained a non-HTTPS wallet base URI", cause)
                    throw e
                }
                is JsonRpc20Client.JsonRpc20Exception -> {
                    Log.e(TAG, "JSON-RPC client exception", cause)
                    throw e
                }
                else -> throw e
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "Request was cancelled", e)
            throw e
        } catch (e: InterruptedException) {
            Log.e(TAG, "Request was interrupted", e)
            throw e
        }
    }

    companion object {
        const val TAG = "MobileWalletAdapter"
        const val ASSOCIATION_TIMEOUT_MS = 10000L
    }
}