package com.solana.mobilewalletadapter.clientlib

import android.app.Activity.RESULT_CANCELED
import android.content.ActivityNotFoundException
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.SessionProperties
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class MobileWalletAdapter(
    private val connectionIdentity: ConnectionIdentity,
    private val timeout: Int = Scenario.DEFAULT_CLIENT_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scenarioProvider: AssociationScenarioProvider = AssociationScenarioProvider(),
) {

    private val adapterOperations = LocalAdapterOperations(ioDispatcher)

    var authToken: String? = null

    /**
     * Specify the RPC cluster used for all operations. Note: changing at runtime will invalidate
     * the auth token and reauthorization will be required
     */
    var blockchain: Blockchain = Solana.Devnet
        set(value) {
            if (value != field) {
                authToken = null
            }

            field = value
        }

    @Deprecated(
        "RpcCluster provides only Solana clusters; use the Blockchain object for full multi-chain support.",
        replaceWith = ReplaceWith("Set `blockchain` property moving forward."),
        DeprecationLevel.WARNING
    )
    var rpcCluster: RpcCluster = RpcCluster.Devnet
        set(value) {
            when (value) {
                RpcCluster.MainnetBeta -> {
                    blockchain = Solana.Mainnet
                }
                RpcCluster.Devnet -> {
                    blockchain = Solana.Devnet
                }
                RpcCluster.Testnet -> {
                    blockchain = Solana.Testnet
                }
                else -> { }
            }

            field = value
        }

    suspend fun connect(sender: ActivityResultSender): TransactionResult<Unit> {
        return transact(sender) { }
    }

    suspend fun <T> transact(
        sender: ActivityResultSender,
        block: suspend AdapterOperations.() -> T,
    ): TransactionResult<T> = coroutineScope {
        return@coroutineScope try {
            val scenario = scenarioProvider.provideAssociationScenario(timeout, SessionProperties.ProtocolVersion.V1)
            val details = scenario.associationDetails()

            val intent = LocalAssociationIntentCreator.createAssociationIntent(
                details.uriPrefix,
                details.port,
                details.session
            )

            try {
                withTimeout(ASSOCIATION_SEND_INTENT_TIMEOUT_MS) {
                    sender.startActivityForResult(intent) {
                        if (it == RESULT_CANCELED) {
                            this.launch {
                                throw InterruptedException()
                            }
                        }
                        launch {
                            delay(5000L)
                            scenario.close()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                return@coroutineScope TransactionResult.Failure("Timed out waiting to send association intent", e)
            } catch (e: InterruptedException) {
                return@coroutineScope TransactionResult.Failure("Request was interrupted", e)
            }

            withContext(ioDispatcher) {
                try {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    val protocolVersion = scenario.session.sessionProperties.protocolVersion

                    val client = scenario.start().get(ASSOCIATION_CONNECT_DISCONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    adapterOperations.client = client

                    val authResult = with (connectionIdentity) {
                        if (protocolVersion == SessionProperties.ProtocolVersion.V1) {
                            /**
                             * TODO: Full MWA 2.0 support has feature & multi-address params. Will be implemented in a future minor release.
                             * Both the features & addresses params are set to null for now.
                             */
                            adapterOperations.authorize(identityUri, iconUri, identityName, blockchain.fullName, authToken, null, null)
                        } else {
                            authToken?.let { token ->
                                adapterOperations.reauthorize(identityUri, iconUri, identityName, token)
                            } ?: run {
                                adapterOperations.authorize(identityUri, iconUri, identityName, RpcCluster.Custom(blockchain.cluster))
                            }.also {
                                authToken = it.authToken
                            }
                        }
                    }

                    val result = block(adapterOperations)

                    TransactionResult.Success(result, authResult)
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
        } catch (e: java.lang.IllegalStateException) {
            return@coroutineScope TransactionResult.Failure(e.message.toString(), e)
        }
    }

    companion object {
        const val TAG = "MobileWalletAdapter"
        const val ASSOCIATION_SEND_INTENT_TIMEOUT_MS = 20000L
        const val ASSOCIATION_CONNECT_DISCONNECT_TIMEOUT_MS = 10000L
    }
}