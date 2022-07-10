/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.usecase

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object SolanaSigningUseCase {
    // throws IllegalArgumentException
    fun signTransaction(
        transaction: ByteArray,
        keypair: AsymmetricCipherKeyPair
    ): Result {
        val publicKey = keypair.public as Ed25519PublicKeyParameters
        val privateKey = keypair.private as Ed25519PrivateKeyParameters
        val publicKeyBytes = publicKey.encoded

        // Validate the transaction only up through the account addresses array
        val (numSignatures, numSignaturesOffset) = readCompactArrayLen(transaction, 0)
        val headerOffset = numSignaturesOffset + (SIGNATURE_LEN * numSignatures)

        val accountsArrayOffset = headerOffset + 3
        require(accountsArrayOffset <= transaction.size) { "transaction header extends beyond buffer bounds" }
        val numSignaturesHeader = transaction[headerOffset].toInt()
        require(numSignatures == numSignaturesHeader) { "Signatures array length does not match transaction required number of signatures" }

        val (numAccounts, numAccountsOffset) = readCompactArrayLen(transaction, accountsArrayOffset)
        require(numAccounts >= numSignatures) { "Accounts array is smaller than number of required signatures" }
        val blockhashOffset = accountsArrayOffset + numAccountsOffset + PUBLIC_KEY_LEN * numAccounts
        require(blockhashOffset <= transaction.size) { "Accounts array extends beyond buffer bounds" }
        var accountIndex = -1
        for (i in 0 until numSignatures) {
            val accountOff = accountsArrayOffset + numAccountsOffset + PUBLIC_KEY_LEN * i
            val accountPublicKey = transaction.copyOfRange(accountOff, accountOff + PUBLIC_KEY_LEN)
            if (publicKeyBytes.contentEquals(accountPublicKey)) {
                accountIndex = i
                break
            }
        }
        require(accountIndex != -1) { "Transaction does not require a signature with the requested keypair" }

        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(transaction, headerOffset, transaction.size - headerOffset)
        val sig = signer.generateSignature()
        assert(sig.size == SIGNATURE_LEN) { "Unexpected signature length" }

        val signedTransaction = transaction.clone()
        System.arraycopy(sig, 0, signedTransaction, numSignaturesOffset + SIGNATURE_LEN * accountIndex, sig.size)

        return Result(signedTransaction, sig)
    }

    fun signMessage(
        message: ByteArray,
        keypair: AsymmetricCipherKeyPair
    ): Result {
        val privateKey = keypair.private as Ed25519PrivateKeyParameters

        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        val sig = signer.generateSignature()
        assert(sig.size == SIGNATURE_LEN) { "Unexpected signature length" }

        val signedMessage = message.copyOf(message.size + SIGNATURE_LEN)
        sig.copyInto(signedMessage, message.size)

        return Result(signedMessage, sig)
    }

    private fun readCompactArrayLen(b: ByteArray, off: Int): Pair<Int, Int> {
        var len: Int

        require(off < b.size) { "compact array length extends beyond buffer bounds" }
        val b0 = b[off].toUByte().toInt()
        len = (b0.and(0x7f))
        if (b0.and(0x80) == 0) {
            return len to 1
        }

        require((off + 1) < b.size) { "compact array length extends beyond buffer bounds" }
        val b1 = b[off + 1].toUByte().toInt()
        len = len.shl(7).or(b1.and(0x7f))
        if (b1.and(0x80) == 0) {
            return len to 2
        }

        require((off + 2) < b.size) { "compact array length extends beyond buffer bounds" }
        val b2 = b[off + 2].toUByte().toInt()
        require(b2.and((0x3).inv()) == 0) { "third byte of compact array length has unexpected bits set" }
        len = len.shl(2).or(b2)
        return len to 3
    }

    const val SIGNATURE_LEN = 64
    const val PUBLIC_KEY_LEN = 32

    data class Result(
        val signedPayload: ByteArray,
        val signature: ByteArray
    )
}