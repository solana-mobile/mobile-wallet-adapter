package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultCaller
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun LocalAssociationScenario.begin(): MobileWalletAdapterClient {
    return start().get(MobileWalletAdapter.ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
}

fun LocalAssociationScenario.end() {
    close().get(MobileWalletAdapter.ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
}

@Suppress("BlockingMethodInNonBlockingContext")
class MobileWalletAdapter(
    private val resultCaller: ActivityResultCaller,
    private val scenarioType: ScenarioTypes = ScenarioTypes.Local,
    private val timeout: Int = Scenario.DEFAULT_CLIENT_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val contract = LocalAssociationIntentContract(LocalAssociationIntentCreator())
    private val resultLauncher = resultCaller.registerForActivityResult(contract) { }

    private val scenarioChooser = ScenarioChooser()

    suspend fun authorize(identityUri: Uri, iconUri: Uri, identityName: String): Boolean {
        return withScenario(associate()) { client ->
            withContext(ioDispatcher) {
                client.authorize(identityUri, iconUri, identityName).get()
                true
            }
        }
    }

    suspend fun reauthorize(identityUri: Uri, iconUri: Uri, identityName: String): Boolean {
        return withScenario(associate()) { client ->
            withContext(ioDispatcher) {
                client.authorize(identityUri, iconUri, identityName).get()
                true
            }
        }
    }

    suspend fun deauthorize(authToken: String): Boolean {
        return withScenario(associate()) { client ->
            withContext(Dispatchers.IO) {
                client.deauthorize(authToken).get()
                true
            }
        }
    }

    suspend fun getCapabilities(): MobileWalletAdapterClient.GetCapabilitiesResult {
        return withScenario(associate()) { client ->
            withContext(ioDispatcher) {
                client.capabilities.get()
            }
        }
    }

    suspend fun signMessage(authToken: String, transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadResult {
        return withScenario(associate()) { client ->
            withContext(ioDispatcher) {
                client.signMessage(authToken, transactions).get()
            }
        }
    }

    suspend fun signTransaction(authToken: String, transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadResult {
        return withScenario(associate()) { client ->
            withContext(ioDispatcher) {
                client.signTransaction(authToken, transactions).get()
            }
        }
    }

    suspend fun signAndSendTransaction(authToken: String, transactions: Array<ByteArray>, params: TransactionParams = DefaultTestnet): MobileWalletAdapterClient.SignAndSendTransactionResult {
        return withScenario(associate()) { client ->
            withContext(ioDispatcher) {
                client.signAndSendTransaction(
                    authToken,
                    transactions,
                    params.commitmentLevel,
                    params.cluster.name,
                    params.skipPreflight,
                    params.preflightCommitment
                ).get()
            }
        }
    }

    private fun associate(): LocalAssociationScenario {
        val scenario = scenarioChooser.chooseScenario<LocalAssociationScenario>(scenarioType, timeout)
        val details = scenario.associationDetails()

        resultLauncher.launch(details)

        return scenario
    }

    /**
     *
     */
    private suspend fun <T> withScenario(scenario: LocalAssociationScenario, block: suspend (MobileWalletAdapterClient) -> T): T {
        return try {
            val client = try {
                scenario.begin()
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while waiting for local association to be ready")
                throw e
            } catch (e: TimeoutException) {
                Log.e(TAG, "Timed out waiting for local association to be ready")
                throw e
            } catch (e: ExecutionException) {
                Log.e(TAG, "Failed establishing local association with wallet", e.cause)
                throw e
            }

            val result = block(client)
            scenario.end()

            result
        } catch (e: Exception) {
            throw e
        }
    }

    //        } catch (e: ExecutionException) {
    //            when (val cause = e.cause) {
    //                is IOException -> Log.e(TAG, "IO error while sending authorize", cause)
    //                is TimeoutException ->
    //                    Log.e(TAG, "Timed out while waiting for authorize result", cause)
    //                is JsonRpc20Client.JsonRpc20RemoteException ->
    //                    when (cause.code) {
    //                        ProtocolContract.ERROR_AUTHORIZATION_FAILED ->
    //                            Log.e(TAG, "Not authorized", cause)
    //                        else ->
    //                            Log.e(TAG, "Remote exception for authorize", cause)
    //                    }
    //                is JsonRpc20Client.JsonRpc20Exception ->
    //                    Log.e(TAG, "JSON-RPC client exception for authorize", cause)
    //                else -> throw e
    //            }
    //        } catch (e: CancellationException) {
    //            Log.e(TAG, "authorize request was cancelled", e)
    //        } catch (e: InterruptedException) {
    //            Log.e(TAG, "authorize request was interrupted", e)
    //        }

    //                is JsonRpc20Client.JsonRpc20Exception ->
    //                    Log.e(TAG, "JSON-RPC client exception for deauthorize", cause)

    //        } catch (e: ExecutionException) {
    //            when (val cause = e.cause) {
    //                is MobileWalletAdapterClient.InvalidPayloadException ->
    //                    Log.e(TAG, "Transaction payload invalid", cause)
    //                is JsonRpc20Client.JsonRpc20RemoteException ->
    //                    when (cause.code) {
    //                        ProtocolContract.ERROR_REAUTHORIZE -> Log.e(TAG, "Reauthorization required", cause)
    //                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", cause)
    //                        ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", cause)
    //                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", cause)
    //                        else -> Log.e(TAG, "Remote exception for sign_transaction", cause)
    //                    }
    //                is JsonRpc20Client.JsonRpc20Exception ->
    //                    Log.e(TAG, "JSON-RPC client exception for sign_transaction", cause)
    //                else -> throw e
    //            }
    //        } catch (e: CancellationException) {
    //            Log.e(TAG, "sign_transaction request was cancelled", e)
    //        } catch (e: InterruptedException) {
    //            Log.e(TAG, "sign_transaction request was interrupted", e)
    //        }

    //        } catch (e: ExecutionException) {
    //            when (val cause = e.cause) {
    //                is IOException -> Log.e(TAG, "IO error while sending sign_message", cause)
    //                is TimeoutException ->
    //                    Log.e(TAG, "Timed out while waiting for sign_message result", cause)
    //                is MobileWalletAdapterClient.InvalidPayloadException ->
    //                    Log.e(TAG, "Message payload invalid", cause)
    //                is JsonRpc20Client.JsonRpc20RemoteException ->
    //                    when (cause.code) {
    //                        ProtocolContract.ERROR_REAUTHORIZE -> Log.e(TAG, "Reauthorization required", cause)
    //                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", cause)
    //                        ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", cause)
    //                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", cause)
    //                        else -> Log.e(TAG, "Remote exception for sign_message", cause)
    //                    }
    //                is JsonRpc20Client.JsonRpc20Exception ->
    //                    Log.e(TAG, "JSON-RPC client exception for sign_message", cause)
    //                else -> throw e
    //            }
    //        } catch (e: CancellationException) {
    //            Log.e(TAG, "sign_message request was cancelled", e)
    //        } catch (e: InterruptedException) {
    //            Log.e(TAG, "sign_message request was interrupted", e)
    //        }

    //        } catch (e: ExecutionException) {
    //            when (val cause = e.cause) {
    //                is IOException ->
    //                    Log.e(TAG, "IO error while sending sign_and_send_transaction", cause)
    //                is TimeoutException ->
    //                    Log.e(TAG, "Timed out while waiting for sign_and_send_transaction result", cause)
    //                is MobileWalletAdapterClient.InvalidPayloadException ->
    //                    Log.e(TAG, "Transaction payload invalid", cause)
    //                is MobileWalletAdapterClient.NotCommittedException -> {
    //                    Log.e(TAG, "Commitment not reached for all transactions", cause)
    //                    signatures = cause.signatures
    //                }
    //                is JsonRpc20Client.JsonRpc20RemoteException ->
    //                    when (cause.code) {
    //                        ProtocolContract.ERROR_REAUTHORIZE -> Log.e(TAG, "Reauthorization required", cause)
    //                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> Log.e(TAG, "Auth token invalid", cause)
    //                        ProtocolContract.ERROR_NOT_SIGNED -> Log.e(TAG, "User did not authorize signing", cause)
    //                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> Log.e(TAG, "Too many payloads to sign", cause)
    //                        else -> Log.e(TAG, "Remote exception for sign_and_send_transaction", cause)
    //                    }
    //                is JsonRpc20Client.JsonRpc20Exception ->
    //                    Log.e(TAG, "JSON-RPC client exception for sign_and_send_transaction", cause)
    //                else -> throw e
    //            }
    //        } catch (e: CancellationException) {
    //            Log.e(TAG, "sign_and_send_transaction request was cancelled", e)
    //        } catch (e: InterruptedException) {
    //            Log.e(TAG, "sign_and_send_transaction request was interrupted", e)
    //        }

    companion object {
        const val TAG = "MobileWalletAdapter"
        const val ASSOCIATION_TIMEOUT_MS = 10000L
    }
}