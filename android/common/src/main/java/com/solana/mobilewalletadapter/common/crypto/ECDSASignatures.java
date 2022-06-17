/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common.crypto;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Size;

import java.util.Arrays;

public class ECDSASignatures {
    // DER ECDSA signature format:
    //  0x30 || total_len (1 byte) ||
    //  0x02 || r_len (1 byte) || r (1..33 bytes) ||
    //  0x02 | s_len (1 byte) || s (1..33 bytes).
    public static final int P256_DER_SIGNATURE_PREFIX_LEN = 2; // 0x30 || 1-byte length
    public static final byte P256_DER_SIGNATURE_PREFIX_TYPE = (byte)0x30;
    public static final int P256_DER_SIGNATURE_MIN_LEN = 8; // 1-byte r and s components
    public static final int P256_DER_SIGNATURE_MAX_LEN = 72; // 33-byte r and s components
    public static final int P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN = 2; // 0x02 || 1-byte length
    public static final byte P256_DER_SIGNATURE_COMPONENT_PREFIX_TYPE = (byte)0x02;
    public static final int P256_DER_SIGNATURE_COMPONENT_MIN_LEN = 1;
    public static final int P256_DER_SIGNATURE_COMPONENT_MAX_LEN = 33;

    public static final int P256_P1363_COMPONENT_LEN = 32;
    public static final int P256_P1363_SIGNATURE_LEN = 64;

    @NonNull
    @Size(P256_P1363_SIGNATURE_LEN)
    public static byte[] convertECP256SignatureDERtoP1363(@NonNull byte[] derSignature,
                                                          @IntRange(from = 0) int offset) {
        if ((offset + P256_DER_SIGNATURE_PREFIX_LEN) > derSignature.length) {
            throw new IllegalArgumentException("DER signature buffer too short to define sequence");
        }

        final byte derType = derSignature[offset];
        if (derType != P256_DER_SIGNATURE_PREFIX_TYPE) {
            throw new IllegalArgumentException("DER signature has invalid type");
        }

        final int derSeqLen = derSignature[offset + 1];

        final byte[] p1363Signature = new byte[P256_P1363_SIGNATURE_LEN];
        final int sOff = unpackDERIntegerToP1363Component(derSignature,
                offset + P256_DER_SIGNATURE_PREFIX_LEN, p1363Signature, 0);
        final int totalOff = unpackDERIntegerToP1363Component(derSignature, sOff, p1363Signature,
                P256_P1363_COMPONENT_LEN);

        if ((offset + P256_DER_SIGNATURE_PREFIX_LEN + derSeqLen) != totalOff) {
            throw new IllegalArgumentException("Invalid DER signature length");
        }

        return p1363Signature;
    }

    private static int unpackDERIntegerToP1363Component(@NonNull byte[] derSignature,
                                                        @IntRange(from = 0) int derOffset,
                                                        @NonNull byte[] p1363Signature,
                                                        @IntRange(from = 0) int p1363Offset) {
        if ((derOffset + P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN) > derSignature.length) {
            throw new IllegalArgumentException("DER signature buffer too short to define component");
        }

        final byte componentDerType = derSignature[derOffset];
        final int componentLen = derSignature[derOffset + 1];

        if (componentDerType != P256_DER_SIGNATURE_COMPONENT_PREFIX_TYPE ||
                componentLen < P256_DER_SIGNATURE_COMPONENT_MIN_LEN ||
                componentLen > P256_DER_SIGNATURE_COMPONENT_MAX_LEN) {
            throw new IllegalArgumentException("DER signature component not well formed");
        } else if ((derOffset + P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN + componentLen) >
                derSignature.length) {
            throw new IllegalArgumentException("DER signature component exceeds buffer length");
        }

        final int copyLen = Math.min(componentLen, P256_P1363_COMPONENT_LEN);
        final int srcOffset = derOffset + P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN +
                componentLen - copyLen;
        final int dstOffset = p1363Offset + P256_P1363_COMPONENT_LEN - copyLen;
        Arrays.fill(p1363Signature, p1363Offset, dstOffset, (byte)0);
        System.arraycopy(derSignature, srcOffset, p1363Signature, dstOffset, copyLen);

        return (derOffset + P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN + componentLen);
    }

    @NonNull
    public static byte[] convertECP256SignatureP1363ToDER(@NonNull byte[] p1363Signature,
                                                          @IntRange(from = 0) int p1363Offset) {
        if ((p1363Offset + P256_P1363_SIGNATURE_LEN) > p1363Signature.length) {
            throw new IllegalArgumentException("Invalid P1363 signature length");
        }

        final int rDerIntLen = calculateDERIntLengthOfP1363Component(p1363Signature, p1363Offset);
        final int sDerIntLen = calculateDERIntLengthOfP1363Component(p1363Signature, p1363Offset + P256_P1363_COMPONENT_LEN);

        final byte[] derSignature = new byte[P256_DER_SIGNATURE_PREFIX_LEN +
                2 * P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN + rDerIntLen + sDerIntLen];
        derSignature[0] = P256_DER_SIGNATURE_PREFIX_TYPE;
        derSignature[1] = (byte)(2 * P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN + rDerIntLen + sDerIntLen);
        int sOff = packP1363ComponentToDERInteger(p1363Signature, p1363Offset, rDerIntLen,
                derSignature, P256_DER_SIGNATURE_PREFIX_LEN);
        int totalLen = packP1363ComponentToDERInteger(p1363Signature,
                p1363Offset + P256_P1363_COMPONENT_LEN, sDerIntLen, derSignature, sOff);
        assert(totalLen == derSignature.length);

        return derSignature;
    }

    @IntRange(from = P256_DER_SIGNATURE_COMPONENT_MIN_LEN, to = P256_DER_SIGNATURE_COMPONENT_MAX_LEN)
    private static int calculateDERIntLengthOfP1363Component(@NonNull byte[] p1363Signature,
                                                             @IntRange(from = 0) int p1363Offset) {
        for (int i = 0; i < P256_P1363_COMPONENT_LEN; i++) {
            final byte val = p1363Signature[p1363Offset + i];
            if (val < 0) {
                return (P256_P1363_COMPONENT_LEN - i + 1);
            } else if (val > 0) {
                return (P256_P1363_COMPONENT_LEN - i);
            }
        }
        return 1;
    }

    private static int packP1363ComponentToDERInteger(@NonNull byte[] p1363Signature,
                                                      @IntRange(from = 0) int p1363Offset,
                                                      @IntRange(from = 1, to = P256_P1363_COMPONENT_LEN + 1) int p1363ComponentDERIntLength,
                                                      @NonNull byte[] derSignature,
                                                      @IntRange(from = 0) int derOffset) {
        derSignature[derOffset] = P256_DER_SIGNATURE_COMPONENT_PREFIX_TYPE;
        derSignature[derOffset + 1] = (byte)p1363ComponentDERIntLength;

        final int leadingBytes = Math.max(0, p1363ComponentDERIntLength - P256_P1363_COMPONENT_LEN);
        final int copyLen = Math.min(p1363ComponentDERIntLength, P256_P1363_COMPONENT_LEN);
        Arrays.fill(derSignature, derOffset + P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN,
                derOffset + P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN + leadingBytes, (byte)0);
        System.arraycopy(p1363Signature, p1363Offset + P256_P1363_COMPONENT_LEN - copyLen,
                derSignature, derOffset + P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN + leadingBytes,
                copyLen);

        return (derOffset + P256_DER_SIGNATURE_COMPONENT_PREFIX_LEN + p1363ComponentDERIntLength);
    }

    private ECDSASignatures() {}
}
