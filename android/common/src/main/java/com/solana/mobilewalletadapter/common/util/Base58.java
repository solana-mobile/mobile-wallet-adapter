/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common.util;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;

public class Base58 {
    @NonNull
    public static byte[] decode(@NonNull final String base58) {
        // Max output size is the same length as the input data (for a base58 string of all '1's)
        final int maxDecodedSize = base58.length();
        final byte[] decoded = new byte[maxDecodedSize];

        // Convert from base58 alphabet characters to the corresponding value
        final byte[] bytes = new byte[maxDecodedSize];
        for (int i = 0; i < bytes.length; i++) {
            final char c = base58.charAt(i);
            bytes[i] = (c <= BASE58_ALPHABET_ASCII_LOOKUP.length ?
                    BASE58_ALPHABET_ASCII_LOOKUP[c] : -1);
            if (bytes[i] == -1) {
                throw new IllegalArgumentException("Character '" + c + "' at [" + i + "] is not a valid base58 character");
            }
        }

        // Skip all leading zeroes; we'll handle these separately at the end
        int start = 0;
        while (start < bytes.length && bytes[start] == 0) {
            start++;
        }
        int zeroes = start;

        int pos = bytes.length - 1; // NOTE: pos can go as low as -1
        while (start < bytes.length) {
            if (bytes[start] == (byte) 0) {
                start++;
            } else {
                int mod = 0;
                for (int i = start; i < bytes.length; i++) {
                    mod = mod * 58 + bytes[i];
                    bytes[i] = (byte) (mod / 256);
                    mod %= 256;
                }
                decoded[pos--] = (byte) mod;
            }
        }

        final byte[] result = new byte[zeroes + bytes.length - pos - 1];
        System.arraycopy(decoded, pos + 1, result, zeroes, bytes.length - pos - 1);
        return result;
    }

    @NonNull
    public static String encode(@NonNull final byte[] bytes) {
        // Max output size is ceil(log2(256) / log2(58) * input_size). In efficient integer math,
        // a slight overestimate of this is (((input_size * 352) + 255) / 256).
        final int maxEncodedSize = (((bytes.length * 352) + 255) / 256);
        final byte[] encoded = new byte[maxEncodedSize];

        int start = 0;
        while (start < bytes.length && bytes[start] == (byte) 0) {
            encoded[start] = BASE58_ALPHABET[0];
            start++;
        }

        // Note: during this processing loop, entries in encoded are bounded to 0..57
        int pos = maxEncodedSize - 1; // NOTE: pos can go as low as -1
        for (int i = start; i < bytes.length; i++) {
            int carry = ((int) bytes[i]) & 0xff; // interpret signed byte as unsigned int
            int j = maxEncodedSize - 1;
            while(carry != 0 || j > pos) {
                carry += encoded[j] * 256;
                encoded[j] = (byte) (carry % 58);
                carry /= 58;
                j--;
            }
            pos = j;
        }

        // Transform encoded into the base58 alphabet
        for (int i = pos + 1; i < maxEncodedSize; i++) {
            encoded[start++] = BASE58_ALPHABET[encoded[i]];
        }

        return new String(encoded, 0, start, StandardCharsets.UTF_8);
    }

    // Not constructable
    private Base58() {}

    private static final byte[] BASE58_ALPHABET_ASCII_LOOKUP = new byte[] {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1,  0,  1,  2,  3,  4,  5,  6,  7,  8, -1, -1, -1, -1, -1, -1,
            -1,  9, 10, 11, 12, 13, 14, 15, 16, -1, 17, 18, 19, 20, 21, -1,
            22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, -1, -1, -1, -1, -1,
            -1, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, -1, 44, 45, 46,
            47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, -1, -1, -1, -1, -1,
    };

    private static final byte[] BASE58_ALPHABET = new byte[] {
            //        1            2            3            4            5            6            7            8
            (byte) 0x31, (byte) 0x32, (byte) 0x33, (byte) 0x34, (byte) 0x35, (byte) 0x36, (byte) 0x37, (byte) 0x38,
            //        9            A            B            C            D            E            F            G
            (byte) 0x39, (byte) 0x41, (byte) 0x42, (byte) 0x43, (byte) 0x44, (byte) 0x45, (byte) 0x46, (byte) 0x47,
            //        H            J            K            L            M            N            P            Q
            (byte) 0x48, (byte) 0x4A, (byte) 0x4B, (byte) 0x4C, (byte) 0x4D, (byte) 0x4E, (byte) 0x50, (byte) 0x51,
            //        R            S            T            U            V            W            X            Y
            (byte) 0x52, (byte) 0x53, (byte) 0x54, (byte) 0x55, (byte) 0x56, (byte) 0x57, (byte) 0x58, (byte) 0x59,
            //        Z            a            b            c            d            e            f            g
            (byte) 0x5A, (byte) 0x61, (byte) 0x62, (byte) 0x63, (byte) 0x64, (byte) 0x65, (byte) 0x66, (byte) 0x67,
            //        h            i            j            k            m            n            o            p
            (byte) 0x68, (byte) 0x69, (byte) 0x6A, (byte) 0x6B, (byte) 0x6D, (byte) 0x6E, (byte) 0x6F, (byte) 0x70,
            //        q            r            s            t            u            v            w            x
            (byte) 0x71, (byte) 0x72, (byte) 0x73, (byte) 0x74, (byte) 0x75, (byte) 0x76, (byte) 0x77, (byte) 0x78,
            //        y            z
            (byte) 0x79, (byte) 0x7A
    };
}
