/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp.usecase

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

// NOTE: this is just a minimal implementation of this Solana RPC call, for testing purposes. It is
// NOT suitable for production use.
object GetLatestBlockhashUseCase {
    @Suppress("BlockingMethodInNonBlockingContext") // running in Dispatchers.IO
    suspend operator fun invoke(rpcUri: Uri): Pair<ByteArray, Int> {
        return withContext(Dispatchers.IO) {
            val conn = URL(rpcUri.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.readTimeout = TIMEOUT_MS
            conn.connectTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.outputStream.use { outputStream ->
                outputStream.write(createGetLatestBlockhashRequest().encodeToByteArray())
            }
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw GetLatestBlockhashFailedException("Response code=${conn.responseCode}")
            }
            val (blockhashBase58, minContextSlot) = conn.inputStream.use { inputStream ->
                val response = inputStream.readBytes().toString(StandardCharsets.UTF_8)
                parseLatestBlockhashResponse(response)
            }
            Log.d(TAG, "getLatestBlockhash blockhash(base58)=$blockhashBase58")
            Base58DecodeUseCase(blockhashBase58) to minContextSlot
        }
    }

    private fun createGetLatestBlockhashRequest(): String {
        val jo = JSONObject()
        jo.put("jsonrpc", "2.0")
        jo.put("id", 1)
        jo.put("method", "getLatestBlockhash")
        val config = JSONObject()
        config.put("commitment", "finalized")
        val arr = JSONArray()
        arr.put(config)
        jo.put("params", arr)

        return jo.toString()
    }

    private fun parseLatestBlockhashResponse(response: String): Pair<String, Int> {
        val jo = JSONObject(response)
        val result = jo.optJSONObject("result")
            ?: throw GetLatestBlockhashFailedException("getLatestBlockhash request was not successful, response=$response")
        val value = result.getJSONObject("value")
        val context = result.getJSONObject("context")
        return value.getString("blockhash") to context.getInt("slot")
    }

    class GetLatestBlockhashFailedException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

    private val TAG = GetLatestBlockhashUseCase::class.simpleName
    private const val TIMEOUT_MS = 20000
}