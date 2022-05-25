package com.solana.mobilewalletadapter.common.crypto;

import androidx.annotation.NonNull;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HKDF {
    @NonNull
    public static byte[] hkdfSHA256L16(@NonNull byte[] ikm, @NonNull byte[] salt) {
        try {
            // Step 1: extract
            final Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
            hmacSHA256.init(new SecretKeySpec(salt, "HmacSHA256"));
            final byte[] prk = hmacSHA256.doFinal(ikm);

            // Step 2: expand
            // Note: N = ceil(L/N) = ceil(16/32) = 1, so only one iteration is required
            hmacSHA256.init(new SecretKeySpec(prk, "HmacSHA256"));
            return hmacSHA256.doFinal(); // first iteration has a 0-byte input
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Error deriving key material", e);
        }
    }

    private HKDF() {}
}
