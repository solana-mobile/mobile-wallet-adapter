package com.solanamobile.mobilewalletadapterwalletlib.reactnative

import com.facebook.react.bridge.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

// Converts a React ReadableArray into a Kotlin ByteArray.
// Expects ReadableArray to be an Array of ints, where each int represents a byte.
internal fun ReadableArray.toByteArray(): ByteArray =
    ByteArray(size()) { index ->
        getInt(index).toByte()
    }

// Converts a Kotlin ByteArray into a React ReadableArray of ints.
internal fun ByteArray.toWritableArray(): ReadableArray =
    Arguments.createArray().apply {
        forEach {
            this.pushInt(it.toInt())
        }
    }
    
internal fun ReadableMap.toJson(): JsonObject = buildJsonObject {
    keySetIterator().let { iterator ->
        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            when (getType(key)) {
                ReadableType.Array -> put(key, getArray(key)!!.toJson())
                ReadableType.Boolean -> put(key, getBoolean(key))
                ReadableType.Map -> put(key, getMap(key)!!.toJson())
                ReadableType.Null -> put(key, JsonNull)
                ReadableType.Number -> put(key, getDouble(key))
                ReadableType.String -> put(key, getString(key))
            }
        }
    }
}

internal fun ReadableArray.toJson(): JsonArray = buildJsonArray {
    for (i in 0 until size()) {
        when (getType(i)) {
            ReadableType.Array -> add(getArray(i)!!.toJson())
            ReadableType.Boolean -> add(getBoolean(i))
            ReadableType.Map -> add(getMap(i)!!.toJson())
            ReadableType.Null -> {}
            ReadableType.Number -> add(getDouble(i))
            ReadableType.String -> add(getString(i))
        }
    }
}

internal fun JsonObject.toReadableMap(): ReadableMap {
    val map: WritableMap = WritableNativeMap()
    val iterator = keys.iterator()
    while (iterator.hasNext()) {
        val key = iterator.next()
        when (val value = this[key]) {
            is JsonPrimitive -> when {
                value.booleanOrNull != null -> map.putBoolean(key, value.boolean)
                value.doubleOrNull != null -> map.putDouble(key, value.double)
                value.intOrNull != null -> map.putInt(key, value.int)
                value.isString -> map.putString(key, value.content)
            }
            is JsonArray -> map.putArray(key, value.toReadableArray())
            is JsonObject -> map.putMap(key, value.toReadableMap())
            else -> map.putString(key, value.toString())
        }
    }
    return map
}

internal fun JsonArray.toReadableArray(): ReadableArray {
    val array: WritableArray = WritableNativeArray()
    for (i in 0 until size) {
        when (val value = this[i]) {
            is JsonPrimitive -> when {
                value.booleanOrNull != null -> array.pushBoolean(value.boolean)
                value.doubleOrNull != null -> array.pushDouble(value.double)
                value.intOrNull != null -> array.pushInt(value.int)
                value.isString -> array.pushString(value.toString())
            }
            is JsonArray -> array.pushArray(value.toReadableArray())
            is JsonObject -> array.pushMap(value.toReadableMap())
            else -> array.pushString(value.toString())
        }
    }
    return array
}