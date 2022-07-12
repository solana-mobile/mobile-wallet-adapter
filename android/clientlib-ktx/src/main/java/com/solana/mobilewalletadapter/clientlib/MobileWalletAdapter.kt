package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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

    private suspend fun <T> withScenario(scenario: LocalAssociationScenario, block: suspend (MobileWalletAdapterClient) -> T): T {
        //TODO: Add unified try/catch handling
        val client = scenario.begin()
        val result = block(client)
        scenario.end()

        return result
    }

    companion object {
        const val ASSOCIATION_TIMEOUT_MS = 10000L
    }
}