/*
 * Copyright (c) 2023 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp.usecase

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.GuardedBy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.whenResumed
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.SessionProperties
import com.solana.mobilewalletadapter.common.protocol.SessionProperties.ProtocolVersion
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object MobileWalletAdapterUseCase {
    private val TAG = MobileWalletAdapterUseCase::class.java.simpleName

    private const val LOCAL_ASSOCIATION_SEND_INTENT_TIMEOUT_MS = 20000L
    private const val LOCAL_ASSOCIATION_START_TIMEOUT_MS =
        60000L // LocalAssociationScenario.start() has a shorter timeout; this is just a backup safety measure
    private const val LOCAL_ASSOCIATION_CLOSE_TIMEOUT_MS = 2000L
    private const val LOCAL_ASSOCIATION_CANCEL_AFTER_WALLET_CLOSED_TIMEOUT_MS = 5000L

    private val mobileWalletAdapterClientSem = Semaphore(1) // allow only a single MWA connection at a time

    data class DappIdentity(
        val uri: Uri? = null, val iconRelativeUri: Uri? = null, val name: String
    )

    class Client(private val client: MobileWalletAdapterClient) {

        suspend fun authorize(
            identity: DappIdentity,
            chain: String?,
            signInPayload: SignInWithSolana.Payload?,
            publicKeys: List<ByteArray>?,
            protocolVersion: ProtocolVersion = ProtocolVersion.V1
        ): MobileWalletAdapterClient.AuthorizationResult = coroutineScope {
            try {
                runInterruptible(Dispatchers.IO) {
                    if (protocolVersion == ProtocolVersion.V1) {
                        client.authorize(
                            identity.uri, identity.iconRelativeUri, identity.name, chain,
                            null, null, publicKeys?.toTypedArray(), signInPayload
                        ).get()!!
                    } else {
                        val cluster = when (chain) {
                            ProtocolContract.CHAIN_SOLANA_MAINNET -> ProtocolContract.CLUSTER_MAINNET_BETA
                            ProtocolContract.CHAIN_SOLANA_TESTNET -> ProtocolContract.CLUSTER_TESTNET
                            ProtocolContract.CHAIN_SOLANA_DEVNET -> ProtocolContract.CLUSTER_DEVNET
                            else -> throw IllegalArgumentException("Provided chain parameter is not valid for a legacy session: $chain")
                        }
                        client.authorize(
                            identity.uri, identity.iconRelativeUri, identity.name, cluster
                        ).get()!!
                    }
                }
            } catch (e: ExecutionException) {
                when (val cause = e.cause) {
                    is IOException -> throw MobileWalletAdapterOperationFailedException(
                        "IO error while sending authorize", cause
                    )
                    is TimeoutException -> throw MobileWalletAdapterOperationFailedException(
                        "Timed out while waiting for authorize result", cause
                    )
                    is JsonRpc20Client.JsonRpc20RemoteException -> when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> throw MobileWalletAdapterOperationFailedException(
                            "Not authorized", cause
                        )
                        ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED -> throw MobileWalletAdapterOperationFailedException(
                            "Cluster not supported", cause
                        )
                        else -> throw MobileWalletAdapterOperationFailedException(
                            "Remote exception for authorize", cause
                        )
                    }
                    is MobileWalletAdapterClient.InsecureWalletEndpointUriException -> throw MobileWalletAdapterOperationFailedException(
                        "authorize result contained a non-HTTPS wallet base URI", cause
                    )
                    is JsonRpc20Client.JsonRpc20Exception -> throw MobileWalletAdapterOperationFailedException(
                        "JSON-RPC client exception for authorize", cause
                    )
                    is IllegalArgumentException -> throw MobileWalletAdapterOperationFailedException(
                        "Invalid chain for legacy session", cause
                    )
                    else -> throw MobileWalletAdapterOperationFailedException(null, e)
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "authorize request was cancelled", e)
                throw e
            }
        }

        suspend fun reauthorize(
            identity: DappIdentity,
            currentAuthToken: String
        ): MobileWalletAdapterClient.AuthorizationResult = coroutineScope {
            try {
                runInterruptible(Dispatchers.IO) {
                    client.reauthorize(
                        identity.uri, identity.iconRelativeUri, identity.name, currentAuthToken
                    ).get()!!
                }
            } catch (e: ExecutionException) {
                when (val cause = e.cause) {
                    is IOException -> throw MobileWalletAdapterOperationFailedException(
                        "IO error while sending reauthorize", cause
                    )
                    is TimeoutException -> throw MobileWalletAdapterOperationFailedException(
                        "Timed out while waiting for reauthorize result", cause
                    )
                    is JsonRpc20Client.JsonRpc20RemoteException -> when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> throw MobileWalletAdapterOperationFailedException(
                            "Not reauthorized", cause
                        )
                        else -> throw MobileWalletAdapterOperationFailedException(
                            "Remote exception for reauthorize", cause
                        )
                    }
                    is MobileWalletAdapterClient.InsecureWalletEndpointUriException -> throw MobileWalletAdapterOperationFailedException(
                        "reauthorize result contained a non-HTTPS wallet base URI", cause
                    )
                    is JsonRpc20Client.JsonRpc20Exception -> throw MobileWalletAdapterOperationFailedException(
                        "JSON-RPC client exception for reauthorize", cause
                    )
                    else -> throw MobileWalletAdapterOperationFailedException(null, e)
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "reauthorize request was cancelled", e)
                throw e
            }
        }

        suspend fun deauthorize(currentAuthToken: String) = coroutineScope {
            try {
                runInterruptible(Dispatchers.IO) { client.deauthorize(currentAuthToken).get()!! }
            } catch (e: ExecutionException) {
                when (val cause = e.cause) {
                    is IOException -> throw MobileWalletAdapterOperationFailedException(
                        "IO error while sending deauthorize", cause
                    )
                    is TimeoutException -> throw MobileWalletAdapterOperationFailedException(
                        "Timed out while waiting for deauthorize result", cause
                    )
                    is JsonRpc20Client.JsonRpc20RemoteException -> throw MobileWalletAdapterOperationFailedException(
                        "Remote exception for deauthorize", cause
                    )
                    is JsonRpc20Client.JsonRpc20Exception -> throw MobileWalletAdapterOperationFailedException(
                        "JSON-RPC client exception for deauthorize", cause
                    )
                    else -> throw MobileWalletAdapterOperationFailedException(null, e)
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "deauthorize request was cancelled", e)
                throw e
            }
        }

        suspend fun getCapabilities(): MobileWalletAdapterClient.GetCapabilitiesResult = coroutineScope {
                try {
                    runInterruptible(Dispatchers.IO) { client.getCapabilities().get()!! }
                } catch (e: ExecutionException) {
                    when (val cause = e.cause) {
                        is IOException -> throw MobileWalletAdapterOperationFailedException(
                            "IO error while sending get_capabilities", cause
                        )
                        is TimeoutException -> throw MobileWalletAdapterOperationFailedException(
                            "Timed out while waiting for get_capabilities result", cause
                        )
                        is JsonRpc20Client.JsonRpc20RemoteException -> throw MobileWalletAdapterOperationFailedException(
                            "Remote exception for get_capabilities", cause
                        )
                        is JsonRpc20Client.JsonRpc20Exception -> throw MobileWalletAdapterOperationFailedException(
                            "JSON-RPC client exception for get_capabilities", cause
                        )
                        else -> throw MobileWalletAdapterOperationFailedException(null, e)
                    }
                } catch (e: CancellationException) {
                    Log.w(TAG, "get_capabilities request was cancelled", e)
                    throw e
                }
            }

        suspend fun signTransactions(transactions: Array<ByteArray>): Array<ByteArray> = coroutineScope {
            try {
                runInterruptible(Dispatchers.IO) {
                    client.signTransactions(transactions).get()!!
                }.signedPayloads
            } catch (e: ExecutionException) {
                when (val cause = e.cause) {
                    is IOException -> throw MobileWalletAdapterOperationFailedException(
                        "IO error while sending sign_transactions", cause
                    )
                    is TimeoutException -> throw MobileWalletAdapterOperationFailedException(
                        "Timed out while waiting for sign_transactions result", cause
                    )
                    is MobileWalletAdapterClient.InvalidPayloadsException -> throw MobileWalletAdapterOperationFailedException(
                        "Transaction payloads invalid", cause
                    )
                    is JsonRpc20Client.JsonRpc20RemoteException -> when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> throw MobileWalletAdapterOperationFailedException(
                            "Authorization invalid, authorization or reauthorization required",
                            cause
                        )
                        ProtocolContract.ERROR_NOT_SIGNED -> throw MobileWalletAdapterOperationFailedException(
                            "User did not authorize signing", cause
                        )
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> throw MobileWalletAdapterOperationFailedException(
                            "Too many payloads to sign", cause
                        )
                        else -> throw MobileWalletAdapterOperationFailedException(
                            "Remote exception for sign_transactions", cause
                        )
                    }
                    is JsonRpc20Client.JsonRpc20Exception -> throw MobileWalletAdapterOperationFailedException(
                        "JSON-RPC client exception for sign_transactions", cause
                    )
                    else -> throw MobileWalletAdapterOperationFailedException(null, e)
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "sign_transactions request was cancelled", e)
                throw e
            }
        }

        suspend fun signMessagesDetached(
            messages: Array<ByteArray>,
            addresses: Array<ByteArray>
        ): Array<MobileWalletAdapterClient.SignMessagesResult.SignedMessage> = coroutineScope {
            try {
                runInterruptible(Dispatchers.IO) {
                    client.signMessagesDetached(messages, addresses).get()!!
                }.messages
            } catch (e: ExecutionException) {
                when (val cause = e.cause) {
                    is IOException -> throw MobileWalletAdapterOperationFailedException(
                        "IO error while sending sign_messages", cause
                    )
                    is TimeoutException -> throw MobileWalletAdapterOperationFailedException(
                        "Timed out while waiting for sign_messages result", cause
                    )
                    is MobileWalletAdapterClient.InvalidPayloadsException -> throw MobileWalletAdapterOperationFailedException(
                        "Message payloads invalid", cause
                    )
                    is JsonRpc20Client.JsonRpc20RemoteException -> when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> throw MobileWalletAdapterOperationFailedException(
                            "Authorization invalid, authorization or reauthorization required",
                            cause
                        )
                        ProtocolContract.ERROR_NOT_SIGNED -> throw MobileWalletAdapterOperationFailedException(
                            "User did not authorize signing", cause
                        )
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> throw MobileWalletAdapterOperationFailedException(
                            "Too many payloads to sign", cause
                        )
                        else -> throw MobileWalletAdapterOperationFailedException(
                            "Remote exception for sign_messages", cause
                        )
                    }
                    is JsonRpc20Client.JsonRpc20Exception -> throw MobileWalletAdapterOperationFailedException(
                        "JSON-RPC client exception for sign_messages", cause
                    )
                    else -> throw MobileWalletAdapterOperationFailedException(null, e)
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "sign_messages request was cancelled", e)
                throw e
            }
        }

        suspend fun signAndSendTransactions(
            transactions: Array<ByteArray>,
            minContextSlot: Int? = null,
            commitment: String? = null,
            skipPreflight: Boolean? = null,
            maxRetries: Int? = null,
            waitForCommitmentToSendNextTransaction: Boolean? = null
        ): Array<ByteArray> = coroutineScope {
            try {
                runInterruptible(Dispatchers.IO) {
                    client.signAndSendTransactions(transactions, minContextSlot, commitment,
                        skipPreflight, maxRetries, waitForCommitmentToSendNextTransaction).get()!!
                }.signatures
            } catch (e: ExecutionException) {
                when (val cause = e.cause) {
                    is IOException -> throw MobileWalletAdapterOperationFailedException(
                        "IO error while sending sign_and_send_transactions", cause
                    )
                    is TimeoutException -> throw MobileWalletAdapterOperationFailedException(
                        "Timed out while waiting for sign_and_send_transactions result", cause
                    )
                    is MobileWalletAdapterClient.InvalidPayloadsException -> throw MobileWalletAdapterOperationFailedException(
                        "Transaction payloads invalid", cause
                    )
                    is MobileWalletAdapterClient.NotSubmittedException -> throw MobileWalletAdapterOperationFailedException(
                        "Not all transactions were submitted", cause
                    )
                    is JsonRpc20Client.JsonRpc20RemoteException -> when (cause.code) {
                        ProtocolContract.ERROR_AUTHORIZATION_FAILED -> throw MobileWalletAdapterOperationFailedException(
                            "Authorization invalid, authorization or reauthorization required",
                            cause
                        )
                        ProtocolContract.ERROR_NOT_SIGNED -> throw MobileWalletAdapterOperationFailedException(
                            "User did not authorize signing", cause
                        )
                        ProtocolContract.ERROR_TOO_MANY_PAYLOADS -> throw MobileWalletAdapterOperationFailedException(
                            "Too many payloads to sign", cause
                        )
                        else -> throw MobileWalletAdapterOperationFailedException(
                            "Remote exception for sign_and_send_transactions", cause
                        )
                    }
                    is JsonRpc20Client.JsonRpc20Exception -> throw MobileWalletAdapterOperationFailedException(
                        "JSON-RPC client exception for sign_and_send_transactions", cause
                    )
                    else -> throw MobileWalletAdapterOperationFailedException(null, e)
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "sign_and_send_transactions request was cancelled", e)
                throw e
            }
        }
    }

    suspend fun <T> localAssociateAndExecute(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>,
        uriPrefix: Uri? = null,
        action: suspend (Client, SessionProperties) -> T
    ): T = localAssociateAndExecuteAsync(intentLauncher, uriPrefix, action).await()

    suspend fun <T> localAssociateAndExecuteAsync(
        intentLauncher: ActivityResultLauncher<StartMobileWalletAdapterActivity.CreateParams>,
        uriPrefix: Uri? = null,
        action: suspend (Client, SessionProperties) -> T
    ): Deferred<T> = coroutineScope {
        // Use async to launch in a new Job, for proper cancellation semantics
        async {
            mobileWalletAdapterClientSem.withPermit {
                val contract = intentLauncher.contract as StartMobileWalletAdapterActivity
                val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)

                val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
                    uriPrefix, localAssociation.port, localAssociation.session
                )
                try {
                    contract.waitForActivityResumed() // may throw TimeoutCancellationException
                } catch (e: TimeoutCancellationException) {
                    throw LocalAssociationFailedException(
                        "Timed out waiting to start Mobile Wallet Adapter Activity", e
                    )
                }
                try {
                    intentLauncher.launch(
                        StartMobileWalletAdapterActivity.CreateParams(
                            associationIntent, this
                        )
                    )
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No Mobile Wallet Adapter-compatible wallet is available", e)
                    throw NoWalletAvailableException(
                        "No Mobile Wallet Adapter Activity available", e
                    )
                }

                withContext(Dispatchers.IO) {
                    try {
                        val mobileWalletAdapterClient = try {
                            runInterruptible {
                                localAssociation.start()
                                    .get(LOCAL_ASSOCIATION_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            }
                        } catch (e: TimeoutException) {
                            throw LocalAssociationFailedException(
                                "Timed out waiting for local association to be ready", e
                            )
                        } catch (e: ExecutionException) {
                            throw LocalAssociationFailedException(
                                "Failed establishing local association with wallet", e.cause
                            )
                        }

                        contract.onMobileWalletAdapterClientConnected(this)

                        action(Client(mobileWalletAdapterClient),
                            localAssociation.session.sessionProperties)
                    } finally {
                        @Suppress("BlockingMethodInNonBlockingContext") // running in Dispatchers.IO; blocking is appropriate
                        localAssociation.close()
                            .get(LOCAL_ASSOCIATION_CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    }
                }
            }
        }
    }

    // Note: do not hold an instance of this class in a member field of the ViewModel. It contains a
    // reference back to the Activity (by way of activityLifecycle). It should only be consumed
    // transiently, as part of invoking localAssociateAndExecute.
    class StartMobileWalletAdapterActivity(private val activityLifecycle: Lifecycle) :
        ActivityResultContract<StartMobileWalletAdapterActivity.CreateParams, ActivityResult>() {
        data class CreateParams(val intent: Intent, val coroutineScope: CoroutineScope)

        @GuardedBy("this")
        private var scope: CoroutineScope? = null

        @GuardedBy("this")
        private var connected: Boolean = false

        override fun createIntent(
            context: Context, input: CreateParams
        ): Intent {
            synchronized(this) {
                scope = input.coroutineScope
                connected = false
            }
            return input.intent
        }

        override fun parseResult(resultCode: Int, intent: Intent?): ActivityResult {
            val scope: CoroutineScope?
            val connected: Boolean
            synchronized(this) {
                scope = this.scope.also { this.scope = null }
                connected = this.connected.also { this.connected = false }
            }

            scope?.let {
                if (connected) {
                    // If the Mobile Wallet Adapter connection was ever established, allow time
                    // for it to terminate gracefully before cancelling the containing Job. This
                    // scope may have already terminated, in which case the Job created by launch
                    // will immediately move to the CANCELED state itself.
                    it.launch {
                        delay(LOCAL_ASSOCIATION_CANCEL_AFTER_WALLET_CLOSED_TIMEOUT_MS)
                        it.cancel()
                    }
                } else {
                    it.cancel() // No-op if the scope is already cancelled
                }
            }
            return ActivityResult(resultCode, intent)
        }

        internal suspend fun waitForActivityResumed() = coroutineScope {
            withTimeout(LOCAL_ASSOCIATION_SEND_INTENT_TIMEOUT_MS) {
                activityLifecycle.whenResumed {}
            }
        }

        internal fun onMobileWalletAdapterClientConnected(scope: CoroutineScope) {
            synchronized(this) {
                this.scope = scope
                this.connected = true
            }
        }
    }

    sealed class LocalAssociateAndExecuteException(message: String?, cause: Throwable?) :
        Exception(message, cause)

    sealed class LocalAssociationException(message: String?, cause: Throwable?) :
        LocalAssociateAndExecuteException(message, cause)

    class LocalAssociationFailedException(message: String?, cause: Throwable?) :
        LocalAssociationException(message, cause)

    class NoWalletAvailableException(message: String?, cause: Throwable?) :
        LocalAssociationException(message, cause)

    class MobileWalletAdapterOperationFailedException(message: String?, cause: Throwable?) :
        LocalAssociateAndExecuteException(message, cause)
}