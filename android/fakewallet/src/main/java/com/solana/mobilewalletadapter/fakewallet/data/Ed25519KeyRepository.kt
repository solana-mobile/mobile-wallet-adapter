/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.data

import android.app.Application
import android.util.Base64
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom

class Ed25519KeyRepository(private val application: Application) {
    private val db by lazy {
        Room.databaseBuilder(application, Ed25519KeyDatabase::class.java, "keys")
            // this is a fake wallet; on schema changes, dropping previously stored keys is fine
            .fallbackToDestructiveMigration()
            .build()
    }

    internal val seed: Flow<Seed?> by lazy { db.seedDao().observe() }

    internal suspend fun getSeed(): Seed? = withContext(Dispatchers.IO) {
        db.seedDao().get()
    }

    suspend fun setSeed(mnemonic: String) = withContext(Dispatchers.IO) {
        db.seedDao().upsert(Seed(mnemonic = mnemonic))
    }

    suspend fun setAccountIndex(accountIndex: Int) = withContext(Dispatchers.IO) {
        db.seedDao().get()?.let { db.seedDao().upsert(it.copy(accountIndex = accountIndex)) }
    }

    suspend fun clearSeed() = withContext(Dispatchers.IO) {
        db.seedDao().delete()
    }

    suspend fun generateKeypair(): AsymmetricCipherKeyPair {
        val kp = withContext(Dispatchers.IO) {
            val kpg = Ed25519KeyPairGenerator()
            kpg.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val keypair = kpg.generateKeyPair()
            val publicKey = keypair.public as Ed25519PublicKeyParameters
            val privateKey = keypair.private as Ed25519PrivateKeyParameters
            val publicKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_PADDING or Base64.NO_WRAP)
            val id = db.keysDao().insert(
                Ed25519KeyPair(publicKeyBase64 = publicKeyBase64, privateKey = privateKey.encoded)
            )
            Log.d(TAG, "Inserted key entry with id=$id for $publicKeyBase64")
            keypair
        }
        return kp
    }

    suspend fun getOrInsertKeypair(privateKeyRaw: ByteArray): AsymmetricCipherKeyPair {
        val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyRaw, 0)
        val publicKeyParams = privateKeyParams.generatePublicKey()
        return getKeypair(publicKeyParams.encoded) ?: withContext(Dispatchers.IO) {
            val publicKeyBase64 = Base64.encodeToString(publicKeyParams.encoded, Base64.NO_PADDING or Base64.NO_WRAP)
            val id = db.keysDao().insert(
                Ed25519KeyPair(publicKeyBase64 = publicKeyBase64, privateKey = privateKeyParams.encoded)
            )
            Log.d(TAG, "Inserted key entry with id=$id for $publicKeyBase64")
            AsymmetricCipherKeyPair(publicKeyParams, privateKeyParams)
        }
    }

    suspend fun getKeypair(publicKeyRaw: ByteArray): AsymmetricCipherKeyPair? {
        val publicKeyBase64 = Base64.encodeToString(publicKeyRaw, Base64.NO_PADDING or Base64.NO_WRAP)
        return withContext(Dispatchers.IO) {
            db.keysDao().get(publicKeyBase64)?.let { keypair ->
                val privateKeyParams = Ed25519PrivateKeyParameters(keypair.privateKey, 0)
                AsymmetricCipherKeyPair(privateKeyParams.generatePublicKey(), privateKeyParams)
            }
        }
    }

    companion object {
        private val TAG = Ed25519KeyRepository::class.simpleName
    }
}