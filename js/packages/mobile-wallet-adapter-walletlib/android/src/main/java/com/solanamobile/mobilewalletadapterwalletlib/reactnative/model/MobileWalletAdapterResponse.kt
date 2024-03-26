package com.solanamobile.mobilewalletadapterwalletlib.reactnative.model

import com.solana.mobilewalletadapter.walletlib.scenario.SignInResult
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.ByteArrayAsMapSerializer
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.ByteArrayCollectionAsMapCollectionSerializer
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.SignInResultSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class MobileWalletAdapterResponse

@Serializable
sealed class MobileWalletAdapterFailureResponse : MobileWalletAdapterResponse()

@Serializable
@SerialName("USER_DECLINED")
object UserDeclinedResponse : MobileWalletAdapterFailureResponse()

@Serializable
@SerialName("TOO_MANY_PAYLOADS")
object TooManyPayloadsResponse : MobileWalletAdapterFailureResponse()

@Serializable
@SerialName("AUTHORIZATION_NOT_VALID")
object AuthorizationNotValidResponse : MobileWalletAdapterFailureResponse()

@Serializable
@SerialName("INVALID_SIGNATURES")
data class InvalidSignaturesResponse(val valid: BooleanArray) : MobileWalletAdapterFailureResponse()

@Serializable
data class AuthorizedAccount(
    @Serializable(with = ByteArrayAsMapSerializer::class) val publicKey: ByteArray,
    val accountLabel: String? = String(publicKey),
    val icon: String? = null,
    val chains: List<String>? = null,
    val features: List<String>? = null
)

@Serializable
data class AuthorizeDappResponse(
    val accounts: List<AuthorizedAccount>,
    val walletUriBase: String? = null,
    @Serializable(with = ByteArrayAsMapSerializer::class) val authorizationScope: ByteArray,
    @Serializable(with = SignInResultSerializer::class) val signInResult: SignInResult? = null
) : MobileWalletAdapterResponse()

@Serializable
data class ReauthorizeDappResponse(
    @Serializable(with = ByteArrayAsMapSerializer::class) val publicKey: ByteArray? = null,
    val accountLabel: String? = publicKey?.let { String(publicKey) },
    val walletUriBase: String? = null,
    @Serializable(with = ByteArrayAsMapSerializer::class) val authorizationScope: ByteArray
) : MobileWalletAdapterResponse()

@Serializable
object DeauthorizeDappResponse : MobileWalletAdapterResponse()

@Serializable
data class SignedPayloads(
    @Serializable(with = ByteArrayCollectionAsMapCollectionSerializer::class) val signedPayloads: List<ByteArray>
) : MobileWalletAdapterResponse()

@Serializable
data class SignedAndSentTransactions(
    @Serializable(with = ByteArrayCollectionAsMapCollectionSerializer::class) val signedTransactions: List<ByteArray>
) : MobileWalletAdapterResponse()
