package com.solanamobile.mobilewalletadapterwalletlib.reactnative

import com.facebook.react.bridge.*
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig

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

internal fun ReadableMap.toMobileWalletAdapterConfig(): MobileWalletAdapterConfig {
    val supportsSignAndSendTransactions = getBoolean("supportsSignAndSendTransactions")
    val maxTransactionsPerSigningRequest = getInt("maxTransactionsPerSigningRequest")
    val maxMessagesPerSigningRequest = getInt("maxMessagesPerSigningRequest")
    val readableArray = getArray("supportedTransactionVersions")
    val supportedTransactionVersions = mutableListOf<Any>()
    for (i in 0 until readableArray?.size()!!) {
        when (readableArray?.getType(i)) {
            ReadableType.Number -> supportedTransactionVersions.add(readableArray.getInt(i))
            ReadableType.String -> supportedTransactionVersions.add(readableArray.getString(i))
            else -> throw IllegalArgumentException("Unsupported type in supportedTransactionVersions array")
        }
    }

    val noConnectionWarningTimeoutMs = getDouble("noConnectionWarningTimeoutMs").toLong()

    return MobileWalletAdapterConfig(
        supportsSignAndSendTransactions,
        maxTransactionsPerSigningRequest,
        maxMessagesPerSigningRequest,
        supportedTransactionVersions.toTypedArray(),
        noConnectionWarningTimeoutMs
    )
}