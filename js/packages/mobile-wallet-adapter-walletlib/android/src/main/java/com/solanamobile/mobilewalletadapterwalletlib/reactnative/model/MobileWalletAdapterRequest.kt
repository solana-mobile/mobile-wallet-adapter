package com.solanamobile.mobilewalletadapterwalletlib.reactnative.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
sealed class MobileWalletAdapterRequest
{
    open val requestId: String = UUID.randomUUID().toString()
    abstract val sessionId: String
    abstract val cluster: String
    abstract val identityName: String?
    abstract val identityUri: String?
    abstract val iconRelativeUri: String?
}

@Serializable
@SerialName("AUTHORIZE_DAPP")
data class AuthorizeDapp(
    override val sessionId: String,
    override val cluster: String,
    override val identityName: String?,
    override val identityUri: String?,
    override val iconRelativeUri: String?
) : MobileWalletAdapterRequest()

@Serializable
sealed class VerifiableIdentityRequestSurrogate : MobileWalletAdapterRequest() {
    abstract val authorizationScope: ByteArray
}

@Serializable
@SerialName("REAUTHORIZE_DAPP")
data class ReauthorizeDapp(
    override val sessionId: String,
    override val cluster: String,
    override val identityName: String?,
    override val identityUri: String?,
    override val iconRelativeUri: String?,
    override val authorizationScope: ByteArray
) : VerifiableIdentityRequestSurrogate()

@Serializable
@SerialName("DEAUTHORIZE_DAPP")
data class DeauthorizeDapp(
    override val sessionId: String,
    override val cluster: String,
    override val identityName: String?,
    override val identityUri: String?,
    override val iconRelativeUri: String?,
    override val authorizationScope: ByteArray
) : VerifiableIdentityRequestSurrogate()

@Serializable
sealed class SignPayloads : VerifiableIdentityRequestSurrogate() {
    abstract val payloads: List<ByteArray>
}

@Serializable
@SerialName("SIGN_MESSAGES")
data class SignMessages(
    override val sessionId: String,
    override val cluster: String,
    override val identityName: String?,
    override val identityUri: String?,
    override val iconRelativeUri: String?,
    override val authorizationScope: ByteArray,
    override val payloads: List<ByteArray>
) : SignPayloads()

@Serializable
@SerialName("SIGN_TRANSACTIONS")
data class SignTransactions(
    override val sessionId: String,
    override val cluster: String,
    override val identityName: String?,
    override val identityUri: String?,
    override val iconRelativeUri: String?,
    override val authorizationScope: ByteArray,
    override val payloads: List<ByteArray>
) : SignPayloads()

@Serializable
@SerialName("SIGN_AND_SEND_TRANSACTIONS")
data class SignAndSendTransactions(
    override val sessionId: String,
    override val cluster: String,
    override val identityName: String?,
    override val identityUri: String?,
    override val iconRelativeUri: String?,
    override val authorizationScope: ByteArray,
    override val payloads: List<ByteArray>
) : SignPayloads()