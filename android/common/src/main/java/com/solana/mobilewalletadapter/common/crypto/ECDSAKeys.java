/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common.crypto;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;

public class ECDSAKeys {
    public static final int ENCODED_PUBLIC_KEY_LENGTH_BYTES = 65;

    @NonNull
    public static byte[] encodeP256PublicKey(@NonNull ECPublicKey ecPublicKey) {
        final ECPoint w = ecPublicKey.getW();
        // NOTE: either x or y could be 33 bytes long, due to BigInteger always including a sign bit
        // in the output. Discard it; we are only interested in the unsigned magnitude.
        final byte[] x = w.getAffineX().toByteArray();
        final byte[] y = w.getAffineY().toByteArray();
        final byte[] encodedPublicKey = new byte[ENCODED_PUBLIC_KEY_LENGTH_BYTES];
        encodedPublicKey[0] = 0x04; // non-compressed public key
        final int xLen = Math.min(x.length, 32);
        final int yLen = Math.min(y.length, 32);
        System.arraycopy(x, x.length - xLen, encodedPublicKey, 33 - xLen, xLen);
        System.arraycopy(y, y.length - yLen, encodedPublicKey, 65 - yLen, yLen);
        return encodedPublicKey;
    }

    @NonNull
    public static ECPublicKey decodeP256PublicKey(@NonNull byte[] encodedPublicKey)
            throws UnsupportedOperationException {
        if (encodedPublicKey.length < ENCODED_PUBLIC_KEY_LENGTH_BYTES ||
                encodedPublicKey[0] != 0x04) {
            throw new IllegalArgumentException("input is not an EC P-256 public key");
        }

        final byte[] x = Arrays.copyOfRange(encodedPublicKey, 1, 33);
        final byte[] y = Arrays.copyOfRange(encodedPublicKey, 33, 65);
        final ECPoint w = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        try {
            final AlgorithmParameters algParams = AlgorithmParameters.getInstance("EC");
            algParams.init(new ECGenParameterSpec("secp256r1"));
            final ECPublicKeySpec ecPublicKeySpec = new ECPublicKeySpec(w,
                    algParams.getParameterSpec(ECParameterSpec.class));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(ecPublicKeySpec);
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException | InvalidKeySpecException e) {
            throw new UnsupportedOperationException("Error decoding EC P-256 public key", e);
        }
    }

    private ECDSAKeys() {}
}
