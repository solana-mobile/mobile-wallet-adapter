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
object RequestAirdropUseCase {
    @Suppress("BlockingMethodInNonBlockingContext") // running in Dispatchers.IO
    suspend operator fun invoke(rpcUri: Uri, publicKey: ByteArray) {
        withContext(Dispatchers.IO) {
            val conn = URL(rpcUri.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.readTimeout = TIMEOUT_MS
            conn.connectTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.outputStream.use { outputStream ->
                outputStream.write(createRequestAirdropRequest(publicKey).encodeToByteArray())
            }
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw AirdropFailedException("Connection failed, response code=${conn.responseCode}")
            }
            val signatureBase58 = conn.inputStream.use { inputStream ->
                val response = inputStream.readBytes().toString(StandardCharsets.UTF_8)
                parseAirdropResponse(response)
            }
            Log.d(TAG, "requestAirdrop pubKey=${Base58EncodeUseCase(publicKey)}, signature(base58)=$signatureBase58")
        }
    }

    private fun createRequestAirdropRequest(publicKey: ByteArray): String {
        val jo = JSONObject()
        jo.put("jsonrpc", "2.0")
        jo.put("id", 1)
        jo.put("method", "requestAirdrop")

        val arr = JSONArray()

        // Parameter 0 - base58-encoded public key
        arr.put(Base58EncodeUseCase(publicKey))

        // Parameter 1 - lamports
        arr.put(AIRDROP_LAMPORTS)

        jo.put("params", arr)

        return jo.toString()
    }

    private fun parseAirdropResponse(response: String): String {
        val jo = JSONObject(response)
        val signature = jo.optString("result")
        if (signature.isEmpty()) {
            throw AirdropFailedException("Airdrop request was not successful, response=$response")
        }
        return signature
    }

    class AirdropFailedException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

    private val TAG = RequestAirdropUseCase::class.simpleName
    private const val TIMEOUT_MS = 20000
    private const val AIRDROP_LAMPORTS = 1000000
}