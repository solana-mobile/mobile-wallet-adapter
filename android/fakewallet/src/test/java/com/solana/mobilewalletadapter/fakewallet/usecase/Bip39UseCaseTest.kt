/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Bip39UseCaseTest {
    @Test
    fun `wordlist has 2048 sorted words`() {
        assertEquals(2048, Bip39UseCase.bip39EnglishWordlist.size)
        assertEquals(
            Bip39UseCase.bip39EnglishWordlist.sorted(),
            Bip39UseCase.bip39EnglishWordlist
        )
        assertEquals("abandon", Bip39UseCase.bip39EnglishWordlist.first())
        assertEquals("zoo", Bip39UseCase.bip39EnglishWordlist.last())
    }

    @Test
    fun `generated phrase is 12 valid words`() {
        val phrase = Bip39UseCase.generatePhrase()
        assertEquals(Bip39UseCase.WORD_COUNT_SHORT, phrase.split(" ").size)
        Bip39UseCase.validate(phrase) // must not throw
    }

    @Test
    fun `validate accepts known good phrase`() {
        Bip39UseCase.validate(TEST_PHRASE)
    }

    @Test
    fun `validate normalizes case and whitespace`() {
        Bip39UseCase.validate("  Abandon ABANDON abandon\tabandon abandon abandon " +
                "abandon abandon abandon\n abandon abandon aboUT ")
    }

    @Test
    fun `validate rejects bad checksum`() {
        assertThrows(IllegalArgumentException::class.java) {
            Bip39UseCase.validate("abandon abandon abandon abandon abandon abandon " +
                    "abandon abandon abandon abandon abandon abandon")
        }
    }

    @Test
    fun `validate rejects non-wordlist word`() {
        assertThrows(IllegalArgumentException::class.java) {
            Bip39UseCase.validate("abandon abandon abandon abandon abandon abandon " +
                    "abandon abandon abandon abandon abandon solana")
        }
    }

    @Test
    fun `validate rejects wrong word count`() {
        assertThrows(IllegalArgumentException::class.java) {
            Bip39UseCase.validate("abandon abandon abandon")
        }
    }

    @Test
    fun `toSeed matches BIP-39 PBKDF2 with empty passphrase`() {
        // Independently computed with Python hashlib:
        // pbkdf2_hmac("sha512", TEST_PHRASE, b"mnemonic", 2048)
        assertEquals(
            "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc1" +
                    "9a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4",
            Bip39UseCase.toSeed(TEST_PHRASE).toHex()
        )
    }

    companion object {
        const val TEST_PHRASE = "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon about"

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
