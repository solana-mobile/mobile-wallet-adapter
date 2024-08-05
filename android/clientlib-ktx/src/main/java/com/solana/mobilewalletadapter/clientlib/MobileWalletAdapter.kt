package com.solana.mobilewalletadapter.clientlib

import android.app.Activity.RESULT_CANCELED
import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Base64
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult.SignInResult
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.common.protocol.SessionProperties
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
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

    private var walletUriBase: Uri? = null

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

    suspend fun connect(sender: ActivityResultSender): TransactionResult<Unit> = transact(sender) {}

    suspend fun disconnect(sender: ActivityResultSender): TransactionResult<Unit> =
        associate(sender) {
            authToken?.let {
                deauthorize(it)
                authToken = null
                walletUriBase = null
            }

            TransactionResult.Success(Unit, null)
        }

    suspend fun signIn(sender: ActivityResultSender,
                       signInPayload: SignInWithSolana.Payload): TransactionResult<SignInResult>
        = when (val result = transact(sender, signInPayload) {}) {
            is TransactionResult.Success -> {
                result.authResult.signInResult?.run {
                    TransactionResult.Success(this, result.authResult)
                } ?: TransactionResult.Failure(
                    "Sign in failed, no sign in result returned by wallet",
                    Exception("no sign in result")
                )
            }
            is TransactionResult.Failure -> TransactionResult.Failure(result.message, result.e)
            is TransactionResult.NoWalletFound -> TransactionResult.NoWalletFound(result.message)
        }

    suspend fun <T> transact(
        sender: ActivityResultSender,
        signInPayload: SignInWithSolana.Payload? = null,
        block: suspend AdapterOperations.(authResult: AuthorizationResult) -> T,
    ): TransactionResult<T> = associate(sender) { sessionProperties ->
        val protocolVersion = sessionProperties.protocolVersion
        val authResult = with (connectionIdentity) {
            if (protocolVersion == SessionProperties.ProtocolVersion.V1) {
                /**
                 * TODO: Full MWA 2.0 support has feature & multi-address params. Will be implemented in a future minor release.
                 * Both the features & addresses params are set to null for now.
                 */
                authorize(identityUri, iconUri, identityName,
                    blockchain.fullName, authToken, null, null, signInPayload)
            } else {
                authToken?.let { token ->
                    reauthorize(identityUri, iconUri, identityName, token)
                } ?: run {
                    authorize(identityUri, iconUri, identityName, blockchain.rpcCluster)
                }
            }.also {
                authToken = it.authToken
                walletUriBase = it.walletUriBase.takeIf { it?.scheme == "https" }

                signInPayload?.run {
                    it.signInResult ?: run {
                        // fallback on signMessages for SIWS result
                        domain = domain ?: identityUri.host
                        val siwsMesage = prepareMessage(addressRaw ?: it.accounts.first().publicKey)
                        val signResult = signMessagesDetached(
                            arrayOf(siwsMesage.encodeToByteArray()),
                            arrayOf(addressRaw!!)
                        ).messages.first()
                        return@with it.with(SignInResult(
                            signResult.addresses.first(),
                            signResult.message,
                            signResult.signatures.first(),
                            "ed25519"
                        ))
                    }
                }
            }
        }

        val result = block(authResult)

        TransactionResult.Success(result, authResult)
    }

    private suspend fun <T> associate(
        sender: ActivityResultSender,
        block: suspend AdapterOperations.(sessionProperties: SessionProperties) -> TransactionResult<T>,
    ): TransactionResult<T> = coroutineScope {
        return@coroutineScope try {
            val scenario = scenarioProvider.provideAssociationScenario(timeout)
            val details = scenario.associationDetails(walletUriBase)

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
                    val client = scenario.start().get(ASSOCIATION_CONNECT_DISCONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    block(LocalAdapterOperations(ioDispatcher, client), scenario.session.sessionProperties)
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
        } catch (e: java.lang.RuntimeException) {
            return@coroutineScope TransactionResult.Failure(e.message.toString(), e)
        }
    }

    companion object {
        const val TAG = "MobileWalletAdapter"
        const val ASSOCIATION_SEND_INTENT_TIMEOUT_MS = 20000L
        const val ASSOCIATION_CONNECT_DISCONNECT_TIMEOUT_MS = 10000L
    }
}