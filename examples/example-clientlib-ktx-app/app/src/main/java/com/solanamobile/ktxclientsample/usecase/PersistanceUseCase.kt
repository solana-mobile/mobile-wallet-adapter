package com.solanamobile.ktxclientsample.usecase

import android.content.SharedPreferences
import android.net.Uri
import com.solana.publickey.SolanaPublicKey
import java.lang.IllegalArgumentException
import javax.inject.Inject

sealed class WalletConnection

object NotConnected : WalletConnection()

data class Connected(
    val publicKey: SolanaPublicKey,
    val accountLabel: String,
    val authToken: String,
    val walletUriBase: Uri? = null
): WalletConnection()

class PersistanceUseCase @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {

    val connected: Connected
        get() = getWalletConnection() as? Connected
            ?: throw IllegalArgumentException("Only use this property when you are sure you have a valid connection.")

    private var connection: WalletConnection = NotConnected

    fun getWalletConnection(): WalletConnection {
        return when(connection) {
            is Connected -> connection
            is NotConnected -> {
                val key = sharedPreferences.getString(PUBKEY_KEY, "")
                val accountLabel = sharedPreferences.getString(ACCOUNT_LABEL, "") ?: ""
                val token = sharedPreferences.getString(AUTH_TOKEN_KEY, "")
                val walletUriBase = sharedPreferences.getString(WALLET_URI_BASE, null)?.run {
                    Uri.parse(this)
                }

                val newConn = if (key.isNullOrEmpty() || token.isNullOrEmpty()) {
                    NotConnected
                } else {
                    Connected(SolanaPublicKey.from(key), accountLabel, token, walletUriBase)
                }

                return newConn
            }
        }
    }

    fun persistConnection(pubKey: SolanaPublicKey, accountLabel: String, token: String, walletUriBase: Uri?) {
        sharedPreferences.edit().apply {
            putString(PUBKEY_KEY, pubKey.base58())
            putString(ACCOUNT_LABEL, accountLabel)
            putString(AUTH_TOKEN_KEY, token)
            putString(WALLET_URI_BASE, walletUriBase.toString())
        }.apply()

        connection = Connected(pubKey, accountLabel, token)
    }

    fun clearConnection() {
        sharedPreferences.edit().apply {
            remove(PUBKEY_KEY)
            remove(ACCOUNT_LABEL)
            remove(AUTH_TOKEN_KEY)
            remove(WALLET_URI_BASE)
        }.apply()

        connection = NotConnected
    }

    companion object {
        const val PUBKEY_KEY = "stored_pubkey"
        const val ACCOUNT_LABEL = "stored_account_label"
        const val AUTH_TOKEN_KEY = "stored_auth_token"
        const val WALLET_URI_BASE = "stored_wallet_uri_base"
    }

}