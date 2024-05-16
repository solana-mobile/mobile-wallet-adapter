package com.solanamobile.mobilewalletadapterwalletlib.reactnative

import android.util.Base64
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import com.solana.mobilewalletadapter.walletlib.scenario.SignInResult
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.AuthorizeDappResponse
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.DeauthorizeDappResponse
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.MobileWalletAdapterFailureResponse
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.MobileWalletAdapterRequest
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.MobileWalletAdapterResponse
import com.solanamobile.mobilewalletadapterwalletlib.reactnative.model.ReauthorizeDappResponse
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONObject

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

internal object AppIdentityTransformingSerializer : JsonTransformingSerializer<MobileWalletAdapterRequest>(MobileWalletAdapterRequest.serializer()) {
    override fun transformSerialize(element: JsonElement): JsonElement =
        if ((element as? JsonObject)?.containsKey("identityUri") == true)
            JsonObject(element.toMutableMap().apply {
                this["appIdentity"] = buildJsonObject {
                    put("identityName", this@apply.remove("identityName") ?: JsonNull)
                    put("identityUri", this@apply.remove("identityUri") ?: JsonNull)
                    put("iconRelativeUri", this@apply.remove("iconRelativeUri") ?: JsonNull)
                }
            })
        else element

    override fun transformDeserialize(element: JsonElement): JsonElement =
        if ((element as? JsonObject)?.containsKey("appIdentity") == true)
            JsonObject(element.toMutableMap().apply {
                val appIdentity = this.remove("appIdentity")!! as JsonObject
                put("identityName", appIdentity["identityName"] ?: JsonNull)
                put("identityUri", appIdentity["identityUri"] ?: JsonNull)
                put("iconRelativeUri", appIdentity["iconRelativeUri"] ?: JsonNull)
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
        else if ((element as? JsonObject)?.containsKey("accounts") == true) AuthorizeDappResponse.serializer()
        else if ((element as? JsonObject)?.containsKey("authorizationScope") == true) ReauthorizeDappResponse.serializer()
        else if ((element as? JsonObject)?.containsKey("signedPayloads") == true) SignedPayloads.serializer()
        else if ((element as? JsonObject)?.containsKey("signedTransactions") == true) SignedAndSentTransactions.serializer() 
        else if ((element as? JsonObject)?.isEmpty() == true) DeauthorizeDappResponse.serializer()
        else MobileWalletAdapterResponse.serializer()
}

internal object MobileWalletAdapterRequestSerializer : TypeTransformingSerializer<MobileWalletAdapterRequest>(AppIdentityTransformingSerializer)

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

object SignInPayloadSerializer : KSerializer<SignInWithSolana.Payload> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor
    
    val json = Json { ignoreUnknownKeys = true }

    override fun deserialize(decoder: Decoder): SignInWithSolana.Payload =
        SignInWithSolana.Payload.fromJson(
            JSONObject(decoder.decodeSerializableValue(JsonElement.serializer()).toString())
        )

    override fun serialize(encoder: Encoder, value: SignInWithSolana.Payload) =
        encoder.encodeSerializableValue(JsonElement.serializer(), 
            json.decodeFromString(JsonElement.serializer(), value.toJson().toString()))
}

object SignInResultSerializer : KSerializer<SignInResult> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): SignInResult =
        decoder.decodeSerializableValue(JsonObject.serializer()).let { json ->
            SignInResult(
                Base64.decode(json["address"]!!.jsonPrimitive.content, Base64.NO_WRAP),
                Base64.decode(json["signed_message"]!!.jsonPrimitive.content, Base64.NO_WRAP),
                Base64.decode(json["signature"]!!.jsonPrimitive.content, Base64.NO_WRAP),
                json["signature_type"]?.jsonPrimitive?.content
            )
        }

    override fun serialize(encoder: Encoder, value: SignInResult) =
        encoder.encodeSerializableValue(JsonElement.serializer(), buildJsonObject { 
            put("address", Base64.encodeToString(value.publicKey, Base64.NO_WRAP))
            put("signed_message", Base64.encodeToString(value.signedMessage, Base64.NO_WRAP))
            put("signature", Base64.encodeToString(value.signature, Base64.NO_WRAP))
            put("signature_type", value.signatureType)
        })
}