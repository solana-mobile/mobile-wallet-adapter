/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.usecase

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Note: this class is for testing purposes only. It does not comprehensively check for error
// results from the RPC server.
object SendTransactionUseCase {
    @Suppress("BlockingMethodInNonBlockingContext") // runs in Dispatchers.IO
    suspend operator fun invoke(
        rpcUri: Uri,
        transactions: Array<ByteArray>,
        commitmentLevel: CommitmentLevel,
        skipPreflight: Boolean,
        preflightCommitment: CommitmentLevel?
    ): BooleanArray {
        var commitmentReached: BooleanArray

        withContext(Dispatchers.IO) {
            // Send all transactions and accumulate transaction signatures
            val signatures = Array<String?>(transactions.size) { null }
            transactions.forEachIndexed { i, transaction ->
                val conn = URL(rpcUri.toString()).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.readTimeout = TIMEOUT_MS
                conn.connectTimeout = TIMEOUT_MS
                conn.doOutput = true
                conn.outputStream.use { outputStream ->
                    outputStream.write(
                        createSendTransactionRequest(
                            transaction,
                            skipPreflight,
                            preflightCommitment
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

            // NOTE: we've verified that every entry in signatures is non-null
            @Suppress("UNCHECKED_CAST")
            signatures as Array<String>

            // Check if all transactions to reach the desired commitment level
            // NOTE: a real wallet would query periodically here; we'll just simply wait a fixed
            // interval and check status
            var attempt = 0
            do {
                delay(COMMITMENT_DELAY_MS)
                Log.d(TAG, "Checking signature statuses (attempt $attempt)")
                val conn = URL(rpcUri.toString()).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.readTimeout = TIMEOUT_MS
                conn.connectTimeout = TIMEOUT_MS
                conn.doOutput = true
                conn.outputStream.use { outputStream ->
                    outputStream.write(createGetSignatureStatusesRequest(signatures).encodeToByteArray())
                }
                conn.connect()
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val result = conn.inputStream.use { inputStream ->
                        inputStream.readBytes()
                    }

                    commitmentReached =
                        parseGetSignatureStatusesResult(transactions.size, commitmentLevel, result)
                            ?: throw CannotVerifySignaturesException("Result did not contain valid signature statuses, response=${String(result)}")
                } else {
                    throw CannotVerifySignaturesException("Response code=${conn.responseCode}")
                }
            } while (commitmentReached.any { !it } && attempt++ < COMMITMENT_RETRIES)
        }

        return commitmentReached
    }

    private fun createSendTransactionRequest(
        transaction: ByteArray,
        skipPreflight: Boolean,
        preflightCommitment: CommitmentLevel?
    ): String {
        val jo = JSONObject()
        jo.put("jsonrpc", "2.0")
        jo.put("id", 1)
        jo.put("method", "sendTransaction")

        val arr = JSONArray()

        // Parameter 0 - base64-encoded transaction
        val transactionBase64 = Base64.encode(transaction, Base64.NO_WRAP)
        arr.put(String(transactionBase64))

        // Parameter 1 - options
        val opt = JSONObject()
        opt.put("encoding", "base64")
        if (skipPreflight) {
            opt.put("skipPreflight", skipPreflight)
        }
        if (preflightCommitment != null) {
            opt.put("preflightCommitment", preflightCommitment.commitmentLevel)
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

    private fun createGetSignatureStatusesRequest(signatures: Array<String>): String {
        val jo = JSONObject()
        jo.put("jsonrpc", "2.0")
        jo.put("id", "1")
        jo.put("method", "getSignatureStatuses")
        val arr = JSONArray(signatures)
        val params = JSONArray()
        params.put(arr)
        jo.put("params", params)
        return jo.toString()
    }

    private fun parseGetSignatureStatusesResult(
        numTransactions: Int,
        commitmentLevel: CommitmentLevel,
        result: ByteArray
    ): BooleanArray? {
        val response = String(result)

        return try {
            val committed = BooleanArray(numTransactions) { false }
            val jo = JSONObject(response)
            val res = jo.getJSONObject("result")
            val arr = res.getJSONArray("value")
            for (i in 0 until arr.length()) {
                committed[i] = arr.optJSONObject(i)?.let { conf ->
                    val confStatus =
                        CommitmentLevel.fromCommitmentLevelString(conf.getString("confirmationStatus"))
                    confStatus != null && confStatus.ordinal >= commitmentLevel.ordinal
                } ?: false
            }
            committed
        } catch (e: JSONException) {
            Log.e(TAG, "Response does not contain signature statuses, response=$response", e)
            return null
        }
    }

    private val TAG = SendTransactionUseCase::class.simpleName
    private const val TIMEOUT_MS = 20000
    private const val COMMITMENT_DELAY_MS = 1000L
    private const val COMMITMENT_RETRIES = 10

    class InvalidTransactionsException(val valid: BooleanArray, message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)
    class CannotVerifySignaturesException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)
}