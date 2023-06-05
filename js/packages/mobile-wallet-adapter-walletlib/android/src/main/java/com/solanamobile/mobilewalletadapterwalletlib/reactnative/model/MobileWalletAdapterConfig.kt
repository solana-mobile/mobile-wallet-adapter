package com.solanamobile.mobilewalletadapterwalletlib.reactnative.model

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class MobileWalletAdapterConfigSurrogate(
    val supportsSignAndSendTransactions: Boolean,
    val maxTransactionsPerSigningRequest: Int,
    val maxMessagesPerSigningRequest: Int,
    val supportedTransactionVersions: List<@Serializable(with = TransactionVersionSerializer::class) Any>,
    val noConnectionWarningTimeoutMs: Long,
)

object TransactionVersionSerializer : JsonContentPolymorphicSerializer<Any>(Any::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out Any> =
        if ((element as? JsonPrimitive)?.content?.toIntOrNull() != null) Int.serializer()
        else if ((element as? JsonPrimitive)?.content == "legacy") String.serializer()
        else throw IllegalArgumentException("supportedTransactionVersions must be either the string \"legacy\" or a non-negative integer")
}

object MobileWalletAdapterConfigSerializer : KSerializer<MobileWalletAdapterConfig> {
    private val delegateSerializer = MobileWalletAdapterConfigSurrogate.serializer()
    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun deserialize(decoder: Decoder): MobileWalletAdapterConfig {
        val surrogate = decoder.decodeSerializableValue(delegateSerializer)
        return MobileWalletAdapterConfig(
            surrogate.supportsSignAndSendTransactions, 
            surrogate.maxTransactionsPerSigningRequest,
            surrogate.maxMessagesPerSigningRequest,
            surrogate.supportedTransactionVersions.toTypedArray(),
            surrogate.noConnectionWarningTimeoutMs
        )
    }

    override fun serialize(encoder: Encoder, value: MobileWalletAdapterConfig) {
        encoder.encodeSerializableValue(delegateSerializer, 
            MobileWalletAdapterConfigSurrogate(
                value.supportsSignAndSendTransactions,
                value.maxTransactionsPerSigningRequest,
                value.maxMessagesPerSigningRequest,
                value.supportedTransactionVersions.toList(),
                value.noConnectionWarningTimeoutMs
            )
        )
    }
}