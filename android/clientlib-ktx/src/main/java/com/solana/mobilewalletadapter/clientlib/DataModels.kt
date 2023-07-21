package com.solana.mobilewalletadapter.clientlib

sealed class TransactionResult<T> {
    data class Success<T>(
        val payload: T
    ): TransactionResult<T>()

    class Failure<T>(
        val message: String,
        val e: Exception
    ): TransactionResult<T>()

    class NoWalletFound<T>(
        val message: String
    ): TransactionResult<T>()
}