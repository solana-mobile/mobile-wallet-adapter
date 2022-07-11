package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Suppress("BlockingMethodInNonBlockingContext")
class MobileWalletAdapter(
    private val resultCaller: ActivityResultCaller,
    private val scenarioType: ScenarioTypes = ScenarioTypes.Local,
    private val timeout: Int = Scenario.DEFAULT_CLIENT_TIMEOUT_MS
) {
    private val contract = LocalAssociationIntentContract(LocalAssociationIntentCreator())
    private val resultLauncher = resultCaller.registerForActivityResult(contract) { }

    private val scenarioChooser = ScenarioChooser()

    private fun associate(): MobileWalletAdapterClient {
        TODO()
    }

    suspend fun authorize(identityUri: Uri, iconUri: Uri, identityName: String) {
        val scenario = scenarioChooser.chooseScenario<LocalAssociationScenario>(scenarioType, timeout)
        val details = scenario.associationDetails()

        resultLauncher.launch(details)

        val client = scenario.start().get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        withContext(Dispatchers.IO) {
            client.authorize(identityUri, iconUri, identityName).get()
        }

        scenario.close().get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    companion object {
        private const val ASSOCIATION_TIMEOUT_MS = 10000L
    }
}