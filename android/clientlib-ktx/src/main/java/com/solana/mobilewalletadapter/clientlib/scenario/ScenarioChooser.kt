package com.solana.mobilewalletadapter.clientlib.scenario

class ScenarioChooser {

    @Suppress("UNCHECKED_CAST") //We will assume consumers know what they are doing
    fun <T : Scenario> chooseScenario(scenarioType: ScenarioTypes, timeout: Int): T {
        return when (scenarioType) {
            ScenarioTypes.Local -> {
                LocalAssociationScenario(timeout) as T
            }
        }
    }

}