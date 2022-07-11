package com.solana.mobilewalletadapter.clientlib

import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import com.solana.mobilewalletadapter.clientlib.scenario.ScenarioTypes

class MobileWalletAdapter(
    private val resultCaller: ActivityResultCaller,
    private val scenarioType: ScenarioTypes = ScenarioTypes.Local
) {

    init {
        resultCaller.registerForActivityResult(ActivityResultContracts.GetContent()) { }
    }

}