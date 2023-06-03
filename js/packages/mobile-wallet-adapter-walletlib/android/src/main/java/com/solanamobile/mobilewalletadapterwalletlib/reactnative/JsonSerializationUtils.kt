package com.solanamobile.mobilewalletadapterwalletlib.reactnative

import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.AuthorizeDappResponse
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.MobileWalletAdapterFailureResponse
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.MobileWalletAdapterRequest
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.MobileWalletAdapterResponse
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.SignedAndSentTransactions
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.SignedPayloads
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

internal open class TypeTransformingSerializer<T: Any>(serializer: KSerializer<T>) : JsonTransformingSerializer<T>(serializer) {
    override fun transformSerialize(element: JsonElement): JsonElement =
        if ((element as? JsonObject)?.containsKey("type") == true)
            JsonObject(element.toMutableMap().apply {
                this["__type"] = this.remove("type")!!
            })
        else element

    override fun transformDeserialize(element: JsonElement): JsonElement =
        if ((element as? JsonObject)?.containsKey("__type") == true)
            JsonObject(element.toMutableMap().apply {
                this["type"] = this.remove("__type")!!
            })
        else element
}

internal object FailReasonTransformingSerializer 
    : JsonTransformingSerializer<MobileWalletAdapterFailureResponse>(MobileWalletAdapterFailureResponse.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if ((element as? JsonObject)?.containsKey("failReason") == true)
            JsonObject(element.toMutableMap().apply {
                this["type"] = this.remove("failReason")!!
            })
        else element
}

internal object MobileWalletAdapterResponseSerializer : JsonContentPolymorphicSerializer<MobileWalletAdapterResponse>(MobileWalletAdapterResponse::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out MobileWalletAdapterResponse> =
        if ((element as? JsonObject)?.containsKey("failReason") == true) FailReasonTransformingSerializer
        else if ((element as? JsonObject)?.containsKey("publicKey") == true) AuthorizeDappResponse.serializer()
        else if ((element as? JsonObject)?.containsKey("signedPayloads") == true) SignedPayloads.serializer()
        else if ((element as? JsonObject)?.containsKey("signedTransactions") == true) SignedAndSentTransactions.serializer() 
        else MobileWalletAdapterResponse.serializer()
}

internal object MobileWalletAdapterRequestSerializer : TypeTransformingSerializer<MobileWalletAdapterRequest>(MobileWalletAdapterRequest.serializer())

internal object ByteArrayAsMapSerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = ByteArraySerializer().descriptor

    override fun deserialize(decoder: Decoder): ByteArray =
        try {
            decoder.decodeSerializableValue(MapSerializer(String.serializer(), Int.serializer())).values.map { it.toByte() }.toByteArray()
        } catch (e: Exception) {
            decoder.decodeSerializableValue(ByteArraySerializer())
        }

    override fun serialize(encoder: Encoder, value: ByteArray) {
        TODO("Not yet implemented")
    }
}

object ByteArrayCollectionAsMapCollectionSerializer : KSerializer<List<ByteArray>> {
    override val descriptor: SerialDescriptor = ListSerializer(ByteArraySerializer()).descriptor

    override fun deserialize(decoder: Decoder): List<ByteArray> =
        decoder.decodeSerializableValue(ListSerializer(ByteArrayAsMapSerializer))

    override fun serialize(encoder: Encoder, value: List<ByteArray>) =
        encoder.encodeSerializableValue(ListSerializer(ByteArrayAsMapSerializer), value)
}