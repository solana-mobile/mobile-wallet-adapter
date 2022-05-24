/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

public class MobileWalletAdapterSession extends MobileWalletAdapterSessionCommon {
    private static final String TAG = MobileWalletAdapterSession.class.getSimpleName();

    @NonNull
    private final KeyPair mAssociationKey;

    public MobileWalletAdapterSession(@NonNull MessageReceiver decryptedPayloadReceiver,
                                      @Nullable StateCallbacks stateCallbacks) {
        super(decryptedPayloadReceiver, stateCallbacks);
        mAssociationKey = generateECP256KeyPair();
    }

    @NonNull
    @Override
    protected ECPublicKey getAssociationPublicKey() {
        return (ECPublicKey) mAssociationKey.getPublic();
    }

    // N.B. Does not need to be synchronized; it consumes only a final immutable object
    @NonNull
    public String encodeAssociationToken() {
        return Base64.encodeToString(encodeECP256PublicKey(
                (ECPublicKey) mAssociationKey.getPublic()),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    @Override
    protected void onReceiverConnected() {
        final ECPublicKey publicKey = generateSessionECDHKeyPair();
        try {
            mMessageSender.send(createHelloReq(mAssociationKey, publicKey));
        } catch (IOException e) {
            Log.e(TAG, "Failed to send HELLO_REQ; terminating session", e);
            onSessionError();
        }
    }

    @NonNull
    private static byte[] createHelloReq(@NonNull KeyPair associationKeyPair,
                                         @NonNull ECPublicKey ourPublicKey) {
        final byte[] ourPublicKeyEncoded = encodeECP256PublicKey(ourPublicKey);

        final byte[] sig;
        try {
            final Signature ecdsaSignature = Signature.getInstance("ECDSA");
            ecdsaSignature.initSign(associationKeyPair.getPrivate());
            ecdsaSignature.update(ourPublicKeyEncoded);
            sig = ecdsaSignature.sign();
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Failed signing HELLO_REQ public key payload");
        }

        final byte[] concatenated = Arrays.copyOf(ourPublicKeyEncoded,
                ourPublicKeyEncoded.length + sig.length);
        System.arraycopy(sig, 0, concatenated, ourPublicKeyEncoded.length, sig.length);
        return concatenated;
    }

    @Override
    protected void handleSessionEstablishmentMessage(@NonNull byte[] payload)
            throws SessionMessageException {
        Log.v(TAG, "handleSessionEstablishmentMessage");

        final ECPublicKey theirPublicKey = parseHelloRsp(payload);
        generateSessionECDHSecret(theirPublicKey);
    }

    @NonNull
    private ECPublicKey parseHelloRsp(@NonNull byte[] message) throws SessionMessageException {
        final ECPublicKey otherPublicKey;
        try {
            otherPublicKey = decodeECP256PublicKey(message);
        } catch (UnsupportedOperationException e) {
            throw new SessionMessageException("Failed creating EC public key from HELLO_RSP", e);
        }

        Log.v(TAG, "Received public key " + otherPublicKey.getW().getAffineX() + "/" +
                otherPublicKey.getW().getAffineY());
        return otherPublicKey;
    }
}
