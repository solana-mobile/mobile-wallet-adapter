/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.usecase

import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Note: this class is for testing purposes only. It does not comprehensively check for error
// results from the RPC server.
object SendTransactionsUseCase {
    @Suppress("BlockingMethodInNonBlockingContext") // runs in Dispatchers.IO
    suspend operator fun invoke(
        rpcUri: Uri,
        transactions: Array<ByteArray>,
        minContextSlot: Int?,
        commitment: String?,
        skipPreflight: Boolean?,
        maxRetries: Int?,
        // TODO: wait for commitment to send next transaction
    ) {
        withContext(Dispatchers.IO) {
            // Send all transactions and accumulate transaction signatures
            val signatures = Array<String?>(transactions.size) { null }
            // TODO: wait for commitment to send next transaction
            transactions.forEachIndexed { i, transaction ->
                val transactionBase64 = Base64.encodeToString(transaction, Base64.NO_WRAP)
                Log.d(TAG, "Sending transaction: '$transactionBase64' with minContextSlot=$minContextSlot")

                val conn = URL(rpcUri.toString()).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.readTimeout = TIMEOUT_MS
                conn.connectTimeout = TIMEOUT_MS
                conn.doOutput = true
                conn.outputStream.use { outputStream ->
                    outputStream.write(
                        createSendTransactionRequest(
                            transactionBase64,
                            minContextSlot,
                            commitment,
                            skipPreflight,
                            maxRetries
                        ).encodeToByteArray()
                    )
                }
                conn.connect()
                signatures[i] = if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val result = conn.inputStream.use { inputStream ->
                        inputStream.readBytes()
                    }
                    try {
                        parseSendTransactionResult(result)
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "sendTransaction did not return a signature, response=${String(result)}")
                        null
                    }
                } else {
                    Log.e(TAG, "Failed sending transaction, response code=${conn.responseCode}")
                    null
                }
            }

            // Ensure all transactions were submitted successfully
            val valid = signatures.map { signature -> signature != null }
            if (valid.any { !it }) {
                throw InvalidTransactionsException(valid.toBooleanArray())
            }
        }
    }

    private fun createSendTransactionRequest(
        transactionBase64: String,
        minContextSlot: Int?,
        commitment: String?,
        skipPreflight: Boolean?,
        maxRetries: Int?
    ): String {
        val jo = JSONObject()
        jo.put("jsonrpc", "2.0")
        jo.put("id", 1)
        jo.put("method", "sendTransaction")

        val arr = JSONArray()

        // Parameter 0 - base64-encoded transaction
        arr.put(transactionBase64)

        // Parameter 1 - options
        val opt = JSONObject()
        opt.put("encoding", "base64")
        opt.put("preflightCommitment", commitment ?: "processed")
        if (minContextSlot != null) {
            opt.put("minContextSlot", minContextSlot)
        }
        if (skipPreflight != null) {
            opt.put("skipPreflight", skipPreflight)
        }
        if (maxRetries != null) {
            opt.put("maxRetries", maxRetries)
        }
        arr.put(opt)

        jo.put("params", arr)

        return jo.toString()
    }

    private fun parseSendTransactionResult(result: ByteArray): String? {
        val response = String(result)
        return try {
            val jo = JSONObject(response)
            jo.getString("result")
        } catch (e: JSONException) {
            Log.e(TAG, "Response does not contain a result value, response=$response", e)
            null
        }
    }

    private val TAG = SendTransactionsUseCase::class.simpleName
    private const val TIMEOUT_MS = 20000

    class InvalidTransactionsException(val valid: BooleanArray, message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)
}