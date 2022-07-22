/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakedapp.usecase

import java.nio.charset.StandardCharsets

object Base58EncodeUseCase {
    operator fun invoke(bytes: ByteArray): String {
        // Max output size is ceil(log2(256) / log2(58) * input_size). In efficient integer math,
        // a slight overestimate of this is (((input_size * 352) + 255) / 256).
        val maxEncodedSize = (((bytes.size * 352) + 255) / 256)
        val encoded = ByteArray(maxEncodedSize)

        var start = 0
        while (start < bytes.size && bytes[start] == 0.toByte()) {
            encoded[start] = BASE58_ALPHABET[0]
            start++
        }

        var pos = maxEncodedSize - 1 // NOTE: pos can go as low as -1
        for (i in start until bytes.size) {
            var carry: Int = bytes[i].toUByte().toInt()
            var j = maxEncodedSize - 1
            while(carry != 0 || j > pos) {
                carry += encoded[j].toUByte().toInt() * 256
                encoded[j] = (carry % 58).toByte()
                carry /= 58
                j--
            }
            pos = j
        }

        for (i in (pos + 1) until maxEncodedSize) {
            encoded[start++] = BASE58_ALPHABET[encoded[i].toInt()]
        }

        return String(encoded, 0, start, StandardCharsets.UTF_8)
    }

    private val BASE58_ALPHABET = byteArrayOf(
        '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte(),
        '5'.code.toByte(), '6'.code.toByte(), '7'.code.toByte(), '8'.code.toByte(),
        '9'.code.toByte(), 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte(),
        'D'.code.toByte(), 'E'.code.toByte(), 'F'.code.toByte(), 'G'.code.toByte(),
        'H'.code.toByte(), 'J'.code.toByte(), 'K'.code.toByte(), 'L'.code.toByte(),
        'M'.code.toByte(), 'N'.code.toByte(), 'P'.code.toByte(), 'Q'.code.toByte(),
        'R'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), 'U'.code.toByte(),
        'V'.code.toByte(), 'W'.code.toByte(), 'X'.code.toByte(), 'Y'.code.toByte(),
        'Z'.code.toByte(), 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(),
        'd'.code.toByte(), 'e'.code.toByte(), 'f'.code.toByte(), 'g'.code.toByte(),
        'h'.code.toByte(), 'i'.code.toByte(), 'j'.code.toByte(), 'k'.code.toByte(),
        'm'.code.toByte(), 'n'.code.toByte(), 'o'.code.toByte(), 'p'.code.toByte(),
        'q'.code.toByte(), 'r'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
        'u'.code.toByte(), 'v'.code.toByte(), 'w'.code.toByte(), 'x'.code.toByte(),
        'y'.code.toByte(), 'z'.code.toByte()
    )
}