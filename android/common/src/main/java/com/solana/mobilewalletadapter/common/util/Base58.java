/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common.util;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;

public class Base58 {
    public static final byte[] BASE58_ALPHABET = new byte[] {
            (byte) '1', (byte) '2', (byte) '3', (byte) '4',
            (byte) '5', (byte) '6', (byte) '7', (byte) '8',
            (byte) '9', (byte) 'A', (byte) 'B', (byte) 'C',
            (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G',
            (byte) 'H', (byte) 'J', (byte) 'K', (byte) 'L',
            (byte) 'M', (byte) 'N', (byte) 'P', (byte) 'Q',
            (byte) 'R', (byte) 'S', (byte) 'T', (byte) 'U',
            (byte) 'V', (byte) 'W', (byte) 'X', (byte) 'Y',
            (byte) 'Z', (byte) 'a', (byte) 'b', (byte) 'c',
            (byte) 'd', (byte) 'e', (byte) 'f', (byte) 'g',
            (byte) 'h', (byte) 'i', (byte) 'j', (byte) 'k',
            (byte) 'm', (byte) 'n', (byte) 'o', (byte) 'p',
            (byte) 'q', (byte) 'r', (byte) 's', (byte) 't',
            (byte) 'u', (byte) 'v', (byte) 'w', (byte) 'x',
            (byte) 'y', (byte) 'z'
    };

    @NonNull
    public static String encode(@NonNull byte[] bytes) {
        // Max output size is ceil(log2(256) / log2(58) * input_size). In efficient integer math,
        // a slight overestimate of this is (((input_size * 352) + 255) / 256).
        final int maxEncodedSize = (((bytes.length * 352) + 255) / 256);
        final byte[] encoded = new byte[maxEncodedSize];

        int start = 0;
        while (start < bytes.length && bytes[start] == 0) {
            encoded[start] = BASE58_ALPHABET[0];
            start++;
        }

        int pos = maxEncodedSize - 1; // NOTE: pos can go as low as -1
        for (int i = start; i < bytes.length; i++) {
            int carry = (int)bytes[i] & 0xFF;
            int j = maxEncodedSize - 1;
            while (carry != 0 || j > pos) {
                carry += ((int)encoded[j] & 0xFF) * 256;
                encoded[j] = (byte)(carry % 58);
                carry /= 58;
                j--;
            }
            pos = j;
        }

        for (int i = pos + 1; i < maxEncodedSize; i++) {
            encoded[start++] = BASE58_ALPHABET[encoded[i]];
        }

        return new String(encoded, 0, start, StandardCharsets.UTF_8);
    }

    private Base58() {}
}
