package com.solana.mobilewalletadapter.clientlib

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult

data class ConnectionIdentity(
    val identityUri: Uri,
    val iconUri: Uri,
    val identityName: String,
)

/**
 * Convenience property to access success payload. Will be null if not successful.
 */
val <T> TransactionResult<T>.successPayload: T?
    get() = (this as? TransactionResult.Success)?.payload

sealed class TransactionResult<T> {
    class Success<T>(
        val payload: T,
        private val result: AuthorizationResult? = null
    ): TransactionResult<T>() {

        val authResult: AuthorizationResult
            get() = try {
                result!!
            } catch (e: NullPointerException) {
                throw IllegalStateException("Auth result accessor is only available when connections credentials have been provided prior to authorize/reauthorize.")
            }
    }

    class Failure<T>(
        val message: String,
        val e: Exception
    ): TransactionResult<T>()

    class NoWalletFound<T>(
        val message: String
    ): TransactionResult<T>()
}