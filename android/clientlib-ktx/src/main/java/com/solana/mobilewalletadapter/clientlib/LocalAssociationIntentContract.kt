package com.solana.mobilewalletadapter.clientlib

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario

/**
 * Details necessary to create the association intent
 */
data class AssociationDetails(
    val uriPrefix: Uri? = null,
    val port: Int,
    val session: MobileWalletAdapterSession
)

fun LocalAssociationScenario.associationDetails(uri: Uri? = null) = AssociationDetails(
    uriPrefix = uri,
    port = this.port,
    session = this.session
)

/**
 * ActivityResultCaller interface so that we can encapsulate and possibly use in better context in the future
 */
internal class LocalAssociationIntentContract : ActivityResultContract<AssociationDetails, Unit>() {

    override fun createIntent(context: Context, input: AssociationDetails): Intent {
        return LocalAssociationIntentCreator.createAssociationIntent(input.uriPrefix, input.port, input.session)
    }

    override fun parseResult(resultCode: Int, intent: Intent?) { }
}