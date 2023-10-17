package com.solana.mobilewalletadapter.clientlib

import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.common.protocol.SessionProperties.ProtocolVersion

class AssociationScenarioProvider {

    val protocolVersions = listOf(
        ProtocolVersion.LEGACY,
        ProtocolVersion.V1
    )

    fun provideAssociationScenario(timeoutMs: Int, maxProtocolVersion: ProtocolVersion = ProtocolVersion.V1): LocalAssociationScenario {
        val protocolSupport = protocolVersions.subList(0, protocolVersions.indexOf(maxProtocolVersion))

        return LocalAssociationScenario(timeoutMs, protocolSupport)
    }

}