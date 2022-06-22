/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp.usecase

import android.util.Log
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import kotlin.random.Random

// NOTE: this is just a minimal implementation of this Solana transaction, for testing purposes. It
// is NOT suitable for production use.
object MemoTransactionUseCase {
    fun create(publicKeyBase58: String, latestBlockhash: ByteArray): ByteArray {
        val transaction = MEMO_TRANSACTION_TEMPLATE.clone()
        val publicKeyBytes = Base58DecodeUseCase(publicKeyBase58)
        assert(publicKeyBytes.size == ACCOUNT_PUBLIC_KEY_LEN) { "Invalid public key length for a Solana transaction" }
        System.arraycopy(publicKeyBytes, 0, transaction, ACCOUNT_PUBLIC_KEY_OFFSET, publicKeyBytes.size)
        assert(latestBlockhash.size == BLOCKHASH_LEN) { "Invalid blockhash length for a Solana transaction" }
        System.arraycopy(latestBlockhash, 0, transaction, BLOCKHASH_OFFSET, latestBlockhash.size)
        for (i in 0 until SUFFIX_DIGITS_LEN) {
            transaction[SUFFIX_DIGITS_OFFSET + i] = ('0'.code + Random.nextInt(10)).toByte()
        }
        Log.d(TAG, "Created memo transaction for publickKey=$publicKeyBase58, latestBlockhash=${latestBlockhash.contentToString()}")
        return transaction
    }

    fun verify(publicKeyBase58: String, signedTransaction: ByteArray) {
        val publicKeyBytes = Base58DecodeUseCase(publicKeyBase58)
        assert(publicKeyBytes.size == ACCOUNT_PUBLIC_KEY_LEN) { "Invalid public key length for a Solana transaction" }

        // First, check that the provided transaction wasn't mangled by the wallet
        require(signedTransaction.size == MEMO_TRANSACTION_TEMPLATE.size) { "Unexpected signed transaction size" }
        val unsignedTransaction = signedTransaction.clone()
        unsignedTransaction.fill(0, SIGNATURE_OFFSET, SIGNATURE_OFFSET + SIGNATURE_LEN)
        unsignedTransaction.fill(0, ACCOUNT_PUBLIC_KEY_OFFSET, ACCOUNT_PUBLIC_KEY_OFFSET + ACCOUNT_PUBLIC_KEY_LEN)
        unsignedTransaction.fill(0, BLOCKHASH_OFFSET, BLOCKHASH_OFFSET + BLOCKHASH_LEN)
        unsignedTransaction.fill(0, SUFFIX_DIGITS_OFFSET, SUFFIX_DIGITS_OFFSET + SUFFIX_DIGITS_LEN)
        require(unsignedTransaction.contentEquals(MEMO_TRANSACTION_TEMPLATE)) { "Signed memo transaction does not match the one sent" }
        val signedTransactionAccount = signedTransaction.copyOfRange(ACCOUNT_PUBLIC_KEY_OFFSET, ACCOUNT_PUBLIC_KEY_OFFSET + ACCOUNT_PUBLIC_KEY_LEN)
        require(signedTransactionAccount.contentEquals(publicKeyBytes)) { "Invalid signing account in transaction" }

        val publicKey = Ed25519PublicKeyParameters(publicKeyBytes, 0)
        val signer = Ed25519Signer()
        signer.init(false, publicKey)
        signer.update(signedTransaction, HEADER_OFFSET, signedTransaction.size - HEADER_OFFSET)
        val signature = signedTransaction.copyOfRange(SIGNATURE_OFFSET, SIGNATURE_OFFSET + SIGNATURE_LEN)
        val verified = signer.verifySignature(signature)
        require(verified) { "Transaction signature is invalid" }
        Log.d(TAG, "Verified memo transaction signature for publickKey=$publicKeyBase58")
    }

    // NOTE: the blockhash of this transaction is fixed, and will be too old to actually execute. It
    // is for test purposes only.
    private val MEMO_TRANSACTION_TEMPLATE = byteArrayOf(
        0x01.toByte(), // 1 signature required (fee payer)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // First signature (fee payer account)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x01.toByte(), // 1 signature required (fee payer)
        0x00.toByte(), // 0 read-only account signatures
        0x01.toByte(), // 1 read-only account not requiring a signature
        0x02.toByte(), // 2 accounts
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Fee payer account public key
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x05.toByte(), 0x4a.toByte(), 0x53.toByte(), 0x5a.toByte(), 0x99.toByte(), 0x29.toByte(), 0x21.toByte(), 0x06.toByte(), // Memo program v2 account address
        0x4d.toByte(), 0x24.toByte(), 0xe8.toByte(), 0x71.toByte(), 0x60.toByte(), 0xda.toByte(), 0x38.toByte(), 0x7c.toByte(),
        0x7c.toByte(), 0x35.toByte(), 0xb5.toByte(), 0xdd.toByte(), 0xbc.toByte(), 0x92.toByte(), 0xbb.toByte(), 0x81.toByte(),
        0xe4.toByte(), 0x1f.toByte(), 0xa8.toByte(), 0x40.toByte(), 0x41.toByte(), 0x05.toByte(), 0x44.toByte(), 0x8d.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Recent blockhash (placeholder)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x01.toByte(), // program ID (index into list of accounts)
        0x01.toByte(), // 1 account
        0x00.toByte(), // account index 0
        0x14.toByte(), // 20 byte payload
        0x68.toByte(), 0x65.toByte(), 0x6c.toByte(), 0x6c.toByte(), 0x6f.toByte(), 0x20.toByte(), 0x77.toByte(), 0x6f.toByte(), // "hello world "
        0x72.toByte(), 0x6c.toByte(), 0x64.toByte(), 0x20.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // 8-digit random suffix
    )

    private val TAG = MemoTransactionUseCase::class.simpleName
    private const val SIGNATURE_OFFSET = 1
    private const val SIGNATURE_LEN = 64
    private const val HEADER_OFFSET = 65
    private const val ACCOUNT_PUBLIC_KEY_OFFSET = 69
    private const val ACCOUNT_PUBLIC_KEY_LEN = 32
    private const val BLOCKHASH_OFFSET = 133
    private const val BLOCKHASH_LEN = 32
    private const val SUFFIX_DIGITS_OFFSET = 181
    private const val SUFFIX_DIGITS_LEN = 8
}