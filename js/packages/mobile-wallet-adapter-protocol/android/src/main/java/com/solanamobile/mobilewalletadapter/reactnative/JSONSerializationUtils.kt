package com.solanamobile.mobilewalletadapter.reactnative

import com.facebook.react.bridge.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object JSONSerializationUtils {
    @Throws(JSONException::class)
    fun convertMapToJson(readableMap: ReadableMap?): JSONObject {
        val json = JSONObject()
        readableMap?.keySetIterator()?.let { iterator ->
            while (iterator.hasNextKey()) {
                val key = iterator.nextKey()
                when (readableMap.getType(key)) {
                    ReadableType.Array -> json.put(
                        key,
                        readableMap.getArray(key)?.let { convertArrayToJson(it) }
                    )
                    ReadableType.Boolean -> json.put(key, readableMap.getBoolean(key))
                    ReadableType.Map -> json.put(key, convertMapToJson(readableMap.getMap(key)))
                    ReadableType.Null -> json.put(key, JSONObject.NULL)
                    ReadableType.Number -> json.put(key, readableMap.getDouble(key))
                    ReadableType.String -> json.put(key, readableMap.getString(key))
                }
            }
        }
        return json
    }

    @Throws(JSONException::class)
    private fun convertArrayToJson(readableArray: ReadableArray): JSONArray {
        val array = JSONArray()
        for (i in 0 until readableArray.size()) {
            when (readableArray.getType(i)) {
                ReadableType.Array -> array.put(convertArrayToJson(readableArray.getArray(i)))
                ReadableType.Boolean -> array.put(readableArray.getBoolean(i))
                ReadableType.Map -> array.put(convertMapToJson(readableArray.getMap(i)))
                ReadableType.Null -> {}
                ReadableType.Number -> array.put(readableArray.getDouble(i))
                ReadableType.String -> array.put(readableArray.getString(i))
            }
        }
        return array
    }

    @Throws(JSONException::class)
    fun convertJsonToMap(jsonObject: JSONObject): ReadableMap {
        val map: WritableMap = WritableNativeMap()
        val iterator = jsonObject.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            when (val value = jsonObject[key]) {
                is Boolean -> map.putBoolean(key, value)
                is Double -> map.putDouble(key, value)
                is Int -> map.putInt(key, value)
                is JSONArray -> map.putArray(key, convertJsonToArray(value))
                is JSONObject -> map.putMap(key, convertJsonToMap(value))
                is String -> map.putString(key, value)
                else -> map.putString(key, value.toString())
            }
        }
        return map
    }

    @Throws(JSONException::class)
    private fun convertJsonToArray(jsonArray: JSONArray): ReadableArray {
        val array: WritableArray = WritableNativeArray()
        for (i in 0 until jsonArray.length()) {
            when (val value = jsonArray[i]) {
                is Boolean -> array.pushBoolean(value)
                is Double -> array.pushDouble(value)
                is Int -> array.pushInt(value)
                is JSONArray -> array.pushArray(convertJsonToArray(value))
                is JSONObject -> array.pushMap(convertJsonToMap(value))
                is String -> array.pushString(value)
                else -> array.pushString(value.toString())
            }
        }
        return array
    }
}