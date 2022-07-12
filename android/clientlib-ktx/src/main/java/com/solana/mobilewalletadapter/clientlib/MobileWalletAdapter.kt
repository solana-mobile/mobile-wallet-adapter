package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.*
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
    private val timeout: Int = Scenario.DEFAULT_CLIENT_TIMEOUT_MS
) {
    private val contract = LocalAssociationIntentContract(LocalAssociationIntentCreator())
    private val resultLauncher = resultCaller.registerForActivityResult(contract) { }

    private val scenarioChooser = ScenarioChooser()

    private fun associate(): LocalAssociationScenario {
        val scenario = scenarioChooser.chooseScenario<LocalAssociationScenario>(scenarioType, timeout)
        val details = scenario.associationDetails()

        resultLauncher.launch(details)

        return scenario
    }

    suspend fun authorize(identityUri: Uri, iconUri: Uri, identityName: String): Boolean {
        val scenario = associate()
        val client = scenario.begin()

        //TODO: Add try/catch handling
        var authorized = false
        authorized = withContext(Dispatchers.IO) {
            client.authorize(identityUri, iconUri, identityName).get()
            true
        }

        scenario.end()

        return authorized
    }

    suspend fun reauthorize(identityUri: Uri, iconUri: Uri, identityName: String): Boolean {
        val scenario = associate()
        val client = scenario.begin()

        //TODO: Add try/catch handling
        var reauthorize = false
        reauthorize = withContext(Dispatchers.IO) {
            client.authorize(identityUri, iconUri, identityName).get()
            true
        }

        scenario.end()

        return reauthorize
    }

    companion object {
        const val ASSOCIATION_TIMEOUT_MS = 10000L
    }
}