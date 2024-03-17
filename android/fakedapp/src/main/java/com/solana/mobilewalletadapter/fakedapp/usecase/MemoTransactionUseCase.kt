/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp.usecase

import android.util.Log
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import kotlin.random.Random

enum class MemoTransactionVersion {
    Legacy, V0
}

// NOTE: this is just a minimal implementation of this Solana transaction, for testing purposes. It
// is NOT suitable for production use.
sealed class MemoTransactionUseCase {
    private val TAG = MemoTransactionUseCase::class.simpleName

    fun create(publicKey: ByteArray, latestBlockhash: ByteArray): ByteArray {
        assert(publicKey.size == ACCOUNT_PUBLIC_KEY_LEN) { "Invalid public key length for a Solana transaction" }
        assert(latestBlockhash.size == BLOCKHASH_LEN) { "Invalid blockhash length for a Solana transaction" }

        val transaction = MEMO_TRANSACTION_TEMPLATE.clone()
        System.arraycopy(publicKey, 0, transaction, ACCOUNT_PUBLIC_KEY_OFFSET, publicKey.size)
        System.arraycopy(latestBlockhash, 0, transaction, BLOCKHASH_OFFSET, latestBlockhash.size)
        for (i in 0 until SUFFIX_DIGITS_LEN) {
            transaction[SUFFIX_DIGITS_OFFSET + i] = ('0'.code + Random.nextInt(10)).toByte()
        }
        Log.d(TAG, "Created memo transaction for publicKey(base58)=${Base58EncodeUseCase(publicKey)}, latestBlockhash(base58)=${Base58EncodeUseCase(latestBlockhash)}")
        return transaction
    }

    fun verify(publicKey: ByteArray, signedTransaction: ByteArray) {
        assert(publicKey.size == ACCOUNT_PUBLIC_KEY_LEN) { "Invalid public key length for a Solana transaction" }
        require(signedTransaction.size == MEMO_TRANSACTION_TEMPLATE.size) { "Unexpected signed transaction size" }

        // First, check that the provided transaction wasn't mangled by the wallet
        val unsignedTransaction = signedTransaction.clone()
        unsignedTransaction.fill(0, SIGNATURE_OFFSET, SIGNATURE_OFFSET + SIGNATURE_LEN)
        unsignedTransaction.fill(0, ACCOUNT_PUBLIC_KEY_OFFSET, ACCOUNT_PUBLIC_KEY_OFFSET + ACCOUNT_PUBLIC_KEY_LEN)
        unsignedTransaction.fill(0, BLOCKHASH_OFFSET, BLOCKHASH_OFFSET + BLOCKHASH_LEN)
        unsignedTransaction.fill(0, SUFFIX_DIGITS_OFFSET, SUFFIX_DIGITS_OFFSET + SUFFIX_DIGITS_LEN)
        require(unsignedTransaction.contentEquals(MEMO_TRANSACTION_TEMPLATE)) { "Signed memo transaction does not match the one sent" }
        val signedTransactionAccount = signedTransaction.copyOfRange(ACCOUNT_PUBLIC_KEY_OFFSET, ACCOUNT_PUBLIC_KEY_OFFSET + ACCOUNT_PUBLIC_KEY_LEN)
        require(signedTransactionAccount.contentEquals(publicKey)) { "Invalid signing account in transaction" }

        val publicKeyParams = Ed25519PublicKeyParameters(publicKey, 0)
        val signer = Ed25519Signer()
        signer.init(false, publicKeyParams)
        signer.update(signedTransaction, HEADER_OFFSET, signedTransaction.size - HEADER_OFFSET)
        val signature = signedTransaction.copyOfRange(SIGNATURE_OFFSET, SIGNATURE_OFFSET + SIGNATURE_LEN)
        val verified = signer.verifySignature(signature)
        require(verified) { "Transaction signature is invalid" }
        Log.d(TAG, "Verified memo transaction signature for publicKey(base58)=${Base58EncodeUseCase(publicKey)}")
    }

    // to be implemented below, not an ideal hierarchy but wanted to share the above code
    abstract val MEMO_TRANSACTION_TEMPLATE: ByteArray

    abstract val SIGNATURE_OFFSET: Int
    abstract val SIGNATURE_LEN: Int
    abstract val HEADER_OFFSET: Int
    abstract val ACCOUNT_PUBLIC_KEY_OFFSET: Int
    abstract val ACCOUNT_PUBLIC_KEY_LEN: Int
    abstract val BLOCKHASH_OFFSET: Int
    abstract val BLOCKHASH_LEN: Int
    abstract val SUFFIX_DIGITS_OFFSET: Int
    abstract val SUFFIX_DIGITS_LEN: Int
}

// Memo Transaction using Legacy Transaction format
object MemoTransactionLegacyUseCase : MemoTransactionUseCase() {
    // NOTE: the blockhash of this transaction is fixed, and will be too old to actually execute. It
    // is for test purposes only.
    override val MEMO_TRANSACTION_TEMPLATE = byteArrayOf(
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
        0x02.toByte(), // 2 read-only account not requiring a signature
        0x03.toByte(), // 3 accounts
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Fee payer account public key
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x03.toByte(), 0x06.toByte(), 0x46.toByte(), 0x6F.toByte(), 0xE5.toByte(), 0x21.toByte(), 0x17.toByte(), 0x32.toByte(), // Compute Budget Program account
        0xFF.toByte(), 0xEC.toByte(), 0xAD.toByte(), 0xBA.toByte(), 0x72.toByte(), 0xC3.toByte(), 0x9B.toByte(), 0xE7.toByte(),
        0xBC.toByte(), 0x8C.toByte(), 0xE5.toByte(), 0xBB.toByte(), 0xC5.toByte(), 0xF7.toByte(), 0x12.toByte(), 0x6B.toByte(),
        0x2C.toByte(), 0x43.toByte(), 0x9B.toByte(), 0x3A.toByte(), 0x40.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x05.toByte(), 0x4a.toByte(), 0x53.toByte(), 0x5a.toByte(), 0x99.toByte(), 0x29.toByte(), 0x21.toByte(), 0x06.toByte(), // Memo program v2 account address
        0x4d.toByte(), 0x24.toByte(), 0xe8.toByte(), 0x71.toByte(), 0x60.toByte(), 0xda.toByte(), 0x38.toByte(), 0x7c.toByte(),
        0x7c.toByte(), 0x35.toByte(), 0xb5.toByte(), 0xdd.toByte(), 0xbc.toByte(), 0x92.toByte(), 0xbb.toByte(), 0x81.toByte(),
        0xe4.toByte(), 0x1f.toByte(), 0xa8.toByte(), 0x40.toByte(), 0x41.toByte(), 0x05.toByte(), 0x44.toByte(), 0x8d.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Recent blockhash (placeholder)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x03.toByte(), // 3 instructions
        0x01.toByte(), // program ID (index into list of accounts)
        0x00.toByte(), // 0 accounts
        0x09.toByte(), // 9 byte payload
        0x03.toByte(), // setComputeUnitPrice
        0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // compute unit price (1 ulamports)
        0x01.toByte(), // program ID (index into list of accounts)
        0x00.toByte(), // 0 accounts
        0x05.toByte(), // 5 byte payload
        0x02.toByte(), // setComputeUnitLimit
        0x93.toByte(), 0x57.toByte(), 0x00.toByte(), 0x00.toByte(), // compute unit limit (22419 units)
        0x02.toByte(), // program ID (index into list of accounts)
        0x01.toByte(), // 1 account
        0x00.toByte(), // account index 0
        0x14.toByte(), // 20 byte payload
        0x68.toByte(), 0x65.toByte(), 0x6c.toByte(), 0x6c.toByte(), 0x6f.toByte(), 0x20.toByte(), 0x77.toByte(), 0x6f.toByte(), // "hello world "
        0x72.toByte(), 0x6c.toByte(), 0x64.toByte(), 0x20.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // 8-digit random suffix
    )

    override val SIGNATURE_OFFSET = 1
    override val SIGNATURE_LEN = 64
    override val HEADER_OFFSET = 65
    override val ACCOUNT_PUBLIC_KEY_OFFSET = 69
    override val ACCOUNT_PUBLIC_KEY_LEN = 32
    override val BLOCKHASH_OFFSET = 165
    override val BLOCKHASH_LEN = 32
    override val SUFFIX_DIGITS_OFFSET = 234
    override val SUFFIX_DIGITS_LEN = 8
}

// Memo Transaction using V0 Transaction format
object MemoTransactionV0UseCase : MemoTransactionUseCase() {
    // NOTE: the blockhash of this transaction is fixed, and will be too old to actually execute. It
    // is for test purposes only.
    override val MEMO_TRANSACTION_TEMPLATE = byteArrayOf(
        //region signature
        0x01.toByte(), // 1 signature required (fee payer)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // First signature (fee payer account)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        //endregion
        0x80.toByte(), // prefix 0b10000000
        //region sign data
        0x01.toByte(), // 1 signature required (fee payer)
        0x00.toByte(), // 0 read-only account signatures
        0x02.toByte(), // 2 read-only account not requiring a signature
        0x03.toByte(), // 3 accounts
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Fee payer account public key
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x03.toByte(), 0x06.toByte(), 0x46.toByte(), 0x6F.toByte(), 0xE5.toByte(), 0x21.toByte(), 0x17.toByte(), 0x32.toByte(), // Compute Budget Program account
        0xFF.toByte(), 0xEC.toByte(), 0xAD.toByte(), 0xBA.toByte(), 0x72.toByte(), 0xC3.toByte(), 0x9B.toByte(), 0xE7.toByte(),
        0xBC.toByte(), 0x8C.toByte(), 0xE5.toByte(), 0xBB.toByte(), 0xC5.toByte(), 0xF7.toByte(), 0x12.toByte(), 0x6B.toByte(),
        0x2C.toByte(), 0x43.toByte(), 0x9B.toByte(), 0x3A.toByte(), 0x40.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x05.toByte(), 0x4a.toByte(), 0x53.toByte(), 0x5a.toByte(), 0x99.toByte(), 0x29.toByte(), 0x21.toByte(), 0x06.toByte(), // Memo program v2 account address
        0x4d.toByte(), 0x24.toByte(), 0xe8.toByte(), 0x71.toByte(), 0x60.toByte(), 0xda.toByte(), 0x38.toByte(), 0x7c.toByte(),
        0x7c.toByte(), 0x35.toByte(), 0xb5.toByte(), 0xdd.toByte(), 0xbc.toByte(), 0x92.toByte(), 0xbb.toByte(), 0x81.toByte(),
        0xe4.toByte(), 0x1f.toByte(), 0xa8.toByte(), 0x40.toByte(), 0x41.toByte(), 0x05.toByte(), 0x44.toByte(), 0x8d.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Recent blockhash (placeholder)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        //endregion
        //region instructions
        0x03.toByte(), // 3 instructions (priority fees + memo)
        0x01.toByte(), // program ID (index into list of accounts)
        0x00.toByte(), // 0 accounts
        0x09.toByte(), // 9 byte payload
        0x03.toByte(), // setComputeUnitPrice
        0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // compute unit price (1 ulamports)
        0x01.toByte(), // program ID (index into list of accounts)
        0x00.toByte(), // 0 accounts
        0x05.toByte(), // 5 byte payload
        0x02.toByte(), // setComputeUnitLimit
        0x93.toByte(), 0x57.toByte(), 0x00.toByte(), 0x00.toByte(), // compute unit limit (22419 units)
        0x02.toByte(), // program ID (index into list of accounts)
        0x01.toByte(), // 1 account
        0x00.toByte(), // account index 0
        0x14.toByte(), // 20 byte payload
        0x68.toByte(), 0x65.toByte(), 0x6c.toByte(), 0x6c.toByte(), 0x6f.toByte(), 0x20.toByte(), 0x77.toByte(), 0x6f.toByte(), // "hello world "
        0x72.toByte(), 0x6c.toByte(), 0x64.toByte(), 0x20.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // 8-digit random suffix
        //endregion
        //region address table lookups
        0x00.toByte(), // 0 address table lookups
        //endregion
    )

    override val SIGNATURE_OFFSET = 1
    override val SIGNATURE_LEN = 64
    override val HEADER_OFFSET = 65
    override val ACCOUNT_PUBLIC_KEY_OFFSET = 70
    override val ACCOUNT_PUBLIC_KEY_LEN = 32
    override val BLOCKHASH_OFFSET = 166
    override val BLOCKHASH_LEN = 32
    override val SUFFIX_DIGITS_OFFSET = 235
    override val SUFFIX_DIGITS_LEN = 8
}