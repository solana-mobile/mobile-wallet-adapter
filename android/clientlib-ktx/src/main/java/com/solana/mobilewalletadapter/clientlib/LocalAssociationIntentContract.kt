package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterSession
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