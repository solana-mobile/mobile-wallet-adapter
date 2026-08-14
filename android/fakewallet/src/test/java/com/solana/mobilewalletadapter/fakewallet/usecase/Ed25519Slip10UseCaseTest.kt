/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.usecase

import com.solana.mobilewalletadapter.fakewallet.usecase.Bip39UseCaseTest.Companion.TEST_PHRASE
import com.solana.mobilewalletadapter.fakewallet.usecase.Bip39UseCaseTest.Companion.toHex
import org.junit.Assert.assertEquals
import org.junit.Test

class Ed25519Slip10UseCaseTest {
    // Expected values computed with an independent Python implementation of
    // BIP-39 + SLIP-0010 (stdlib hashlib/hmac) that was first validated against
    // the official SLIP-0010 ed25519 test vectors 1 and 2.
    @Test
    fun `derives Solana private key at account 0`() {
        assertEquals(
            "37df573b3ac4ad5b522e064e25b63ea16bcbe79d449e81a0268d1047948bb445",
            Ed25519Slip10UseCase.derivePrivateKey(Bip39UseCase.toSeed(TEST_PHRASE), 0).toHex()
        )
    }

    @Test
    fun `derives Solana private key at account 1`() {
        assertEquals(
            "ba5e7b6e3680b4eb81db8e54c8e466b2e9a899355888403355d858ab985d2fc4",
            Ed25519Slip10UseCase.derivePrivateKey(Bip39UseCase.toSeed(TEST_PHRASE), 1).toHex()
        )
    }

    @Test
    fun `derivation path string follows the Solana standard`() {
        assertEquals("m/44'/501'/0'/0'", Ed25519Slip10UseCase.derivationPath(0))
        assertEquals("m/44'/501'/7'/0'", Ed25519Slip10UseCase.derivationPath(7))
    }
}
