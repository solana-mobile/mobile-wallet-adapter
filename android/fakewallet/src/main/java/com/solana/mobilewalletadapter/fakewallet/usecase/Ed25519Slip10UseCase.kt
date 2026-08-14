/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.usecase

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// SLIP-0010 ed25519 key derivation, adapted from the SeedVaultSimulator implementation
// (solana-mobile/seed-vault-sdk, Ed25519Slip10UseCase.kt), reduced to the fixed
// Solana derivation path m/44'/501'/account'/0' (all levels hardened)
object Ed25519Slip10UseCase {
    private const val MASTER_SECRET_MAC_KEY = "ed25519 seed"
    private const val MAC = "HmacSHA512"

    private const val BIP44_PURPOSE = 44
    private const val BIP44_COIN_TYPE_SOLANA = 501

    fun derivationPath(accountIndex: Int): String = "m/44'/501'/$accountIndex'/0'"

    // Returns the 32-byte ed25519 private key seed for m/44'/501'/accountIndex'/0'
    fun derivePrivateKey(bip39Seed: ByteArray, accountIndex: Int): ByteArray {
        var node = deriveMasterSecret(bip39Seed)
        for (level in intArrayOf(BIP44_PURPOSE, BIP44_COIN_TYPE_SOLANA, accountIndex, 0)) {
            node = deriveChildSecretKey(node, level)
        }
        return node.copyOf(32)
    }

    private fun deriveMasterSecret(bip39Seed: ByteArray): ByteArray =
        hmacSha512(MASTER_SECRET_MAC_KEY.encodeToByteArray(), bip39Seed)

    // node is the 64-byte key (0-32) || chain code (32-64); index is always hardened
    private fun deriveChildSecretKey(node: ByteArray, index: Int): ByteArray {
        val hardenedIndex = index.toLong() or 0x80000000L
        val data = ByteArray(37)
        node.copyInto(data, 1, 0, 32)
        data[33] = (hardenedIndex shr 24).toByte()
        data[34] = (hardenedIndex shr 16).toByte()
        data[35] = (hardenedIndex shr 8).toByte()
        data[36] = hardenedIndex.toByte()
        return hmacSha512(node.copyOfRange(32, 64), data)
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val hmac = Mac.getInstance(MAC)
        hmac.init(SecretKeySpec(key, MAC))
        return hmac.doFinal(data)
    }
}
