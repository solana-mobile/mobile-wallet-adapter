package com.solanamobile.ktxclientsample.usecase

import android.content.SharedPreferences
import com.portto.solana.web3.PublicKey
import javax.inject.Inject

data class WalletConnection(
    val publickKey: PublicKey,
    val authToken: String
)

class PersistanceUseCase @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {

    fun getWalletConnection() {

    }

    fun persistConnection(pubKey: PublicKey, token: String) {
        sharedPreferences.edit().apply {
            putString(PUBKEY_KEY, pubKey.toBase58())
            putString(AUTH_TOKEN_KEY, token)
        }.apply()
    }

    companion object {
        const val PUBKEY_KEY = "stored_pubkey"
        const val AUTH_TOKEN_KEY = "stored_auth_token"
    }

}