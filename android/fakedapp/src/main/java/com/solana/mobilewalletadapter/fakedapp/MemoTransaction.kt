/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object MemoTransaction {
    fun create(publicKeyBase58: String): ByteArray {
        val transaction = MEMO_TRANSACTION_TEMPLATE.clone()
        val publicKeyBytes = Base58DecodeUseCase(publicKeyBase58)
        assert(publicKeyBytes.size == 32) { "Invalid public key length for a Solana transaction" }
        System.arraycopy(publicKeyBytes, 0, transaction, ACCOUNT_PUBLIC_KEY_OFFSET, publicKeyBytes.size)
        return transaction
    }

    fun verify(publicKeyBase58: String, signedTransaction: ByteArray) {
        val publicKeyBytes = Base58DecodeUseCase(publicKeyBase58)
        assert(publicKeyBytes.size == 32) { "Invalid public key length for a Solana transaction" }

        // First, check that the provided transaction wasn't mangled by the wallet
        require(signedTransaction.size == MEMO_TRANSACTION_TEMPLATE.size) { "Unexpected signed transaction size" }
        val unsignedTransaction = signedTransaction.clone()
        unsignedTransaction.fill(0, SIGNATURE_OFFSET, HEADER_OFFSET)
        unsignedTransaction.fill(0, ACCOUNT_PUBLIC_KEY_OFFSET, ACCOUNT_PUBLIC_KEY_OFFSET + 32)
        require(unsignedTransaction.contentEquals(MEMO_TRANSACTION_TEMPLATE)) { "Signed memo transaction does not match the one sent" }
        val signedTransactionAccount = signedTransaction.copyOfRange(ACCOUNT_PUBLIC_KEY_OFFSET, ACCOUNT_PUBLIC_KEY_OFFSET + 32)
        require(signedTransactionAccount.contentEquals(publicKeyBytes)) { "Invalid signing account in transaction" }

        val publicKey = Ed25519PublicKeyParameters(publicKeyBytes, 0)
        val signer = Ed25519Signer()
        signer.init(false, publicKey)
        signer.update(signedTransaction, HEADER_OFFSET, signedTransaction.size - HEADER_OFFSET)
        val signature = signedTransaction.copyOfRange(SIGNATURE_OFFSET, HEADER_OFFSET)
        val verified = signer.verifySignature(signature)
        require(verified) { "Transaction signature is invalid" }
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
        0xa2.toByte(), 0x53.toByte(), 0x0d.toByte(), 0xb2.toByte(), 0x8d.toByte(), 0x52.toByte(), 0xd1.toByte(), 0x42.toByte(), // Recent blockhash (not really in this case, though)
        0x9a.toByte(), 0xac.toByte(), 0x32.toByte(), 0xc8.toByte(), 0x6e.toByte(), 0x47.toByte(), 0xd4.toByte(), 0x07.toByte(),
        0x74.toByte(), 0xe6.toByte(), 0x79.toByte(), 0x3d.toByte(), 0xf1.toByte(), 0xe7.toByte(), 0xf3.toByte(), 0x2b.toByte(),
        0x69.toByte(), 0xfa.toByte(), 0x4c.toByte(), 0x76.toByte(), 0xfc.toByte(), 0xcb.toByte(), 0xb4.toByte(), 0x01.toByte(),
        0x01.toByte(), // program ID (index into list of accounts)
        0x01.toByte(), // 1 account
        0x00.toByte(), // account index 0
        0x0b.toByte(), // 11 byte payload
        0x68.toByte(), 0x65.toByte(), 0x6c.toByte(), 0x6c.toByte(), 0x6f.toByte(), 0x20.toByte(), 0x77.toByte(), 0x6f.toByte(), // "hello world"
        0x72.toByte(), 0x6c.toByte(), 0x64.toByte(),
    )

    private const val SIGNATURE_OFFSET = 1
    private const val HEADER_OFFSET = 65
    private const val ACCOUNT_PUBLIC_KEY_OFFSET = 69
}