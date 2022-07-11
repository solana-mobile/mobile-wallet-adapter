package com.solana.mobilewalletadapter.clientlib

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario

/**
 *
 */
internal class LocalAssociationIntentContract(
    private val localScenario: LocalAssociationScenario
) : ActivityResultContract<Unit, Unit>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return localScenario.createAssociationIntent()
    }

    override fun parseResult(resultCode: Int, intent: Intent?) { }
}