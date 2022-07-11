package com.solana.mobilewalletadapter.clientlib

import androidx.activity.result.ActivityResultCaller
import com.solana.mobilewalletadapter.clientlib.scenario.*

class MobileWalletAdapter(
    private val resultCaller: ActivityResultCaller,
    private val scenarioType: ScenarioTypes = ScenarioTypes.Local,
    private val timeout: Int = Scenario.DEFAULT_CLIENT_TIMEOUT_MS
) {
    private val contract = LocalAssociationIntentContract(LocalAssociationIntentCreator())
    private val resultLauncher = resultCaller.registerForActivityResult(contract) { }

    private val scenarioChooser = ScenarioChooser()

    fun getThisStuffReady() {
        val scenario = scenarioChooser.chooseScenario<LocalAssociationScenario>(scenarioType, timeout)
        val details = scenario.associationDetails()

        resultLauncher.launch(details)
    }

}