/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp.usecase

object Base58DecodeUseCase {
    // throws IllegalArgumentException if base58 input string contains a non-base58 character
    operator fun invoke(base58: String): ByteArray {
        // Max output size is the same length as the input data (for a base58 string of all '1's)
        val maxDecodedSize = base58.length
        val decoded = ByteArray(maxDecodedSize)

        // Convert from base58 alphabet characters to the corresponding value
        val bytes = ByteArray(base58.length) { i ->
            val c = base58[i].code
            val b = if (c <= BASE58_ALPHABET_ASCII_LOOKUP.size) BASE58_ALPHABET_ASCII_LOOKUP[c] else -1
            require(b != (-1).toByte()) { "Character '$c' at [$i] is not a valid base58 character" }
            b
        }

        // Skip all leading zeroes; we'll handle these separately at the end
        var start = 0
        while (start < bytes.size && bytes[start] == 0.toByte()) {
            start++
        }
        val zeroes = start

        var pos = bytes.size - 1 // NOTE: pos can go as low as -1
        while (start < bytes.size) {
            if (bytes[start] == 0.toByte()) {
                start++
            } else {
                var mod = 0
                for (i in start until bytes.size) {
                    mod = mod * 58 + bytes[i]
                    bytes[i] = (mod / 256).toByte()
                    mod %= 256
                }
                decoded[pos--] = mod.toByte()
            }
        }

        val result = ByteArray(zeroes + bytes.size - pos - 1)
        System.arraycopy(decoded, pos + 1, result, zeroes, bytes.size - pos - 1)
        return result
    }

    private val BASE58_ALPHABET_ASCII_LOOKUP = byteArrayOf(
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1,  0,  1,  2,  3,  4,  5,  6,  7,  8, -1, -1, -1, -1, -1, -1,
        -1,  9, 10, 11, 12, 13, 14, 15, 16, -1, 17, 18, 19, 20, 21, -1,
        22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, -1, -1, -1, -1, -1,
        -1, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, -1, 44, 45, 46,
        47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, -1, -1, -1, -1, -1,
    )
}