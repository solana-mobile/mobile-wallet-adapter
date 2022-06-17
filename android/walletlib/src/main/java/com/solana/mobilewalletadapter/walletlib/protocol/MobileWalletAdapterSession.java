/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.crypto.ECDSAKeys;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.common.crypto.ECDSASignatures;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;

public class MobileWalletAdapterSession extends MobileWalletAdapterSessionCommon {
    private static final String TAG = MobileWalletAdapterSession.class.getSimpleName();

    @NonNull
    private final Scenario mScenario;
    @NonNull
    private final ECPublicKey mAssociationPublicKey;

    public MobileWalletAdapterSession(@NonNull Scenario scenario,
                                      @NonNull MessageReceiver decryptedPayloadReceiver,
                                      @Nullable StateCallbacks stateCallbacks) {
        super(decryptedPayloadReceiver, stateCallbacks);
        mScenario = scenario;
        mAssociationPublicKey = ECDSAKeys.decodeP256PublicKey(scenario.associationPublicKey);
    }

    @NonNull
    @Override
    protected ECPublicKey getAssociationPublicKey() {
        return mAssociationPublicKey;
    }

    @Override
    protected void handleSessionEstablishmentMessage(@NonNull byte[] payload)
            throws SessionMessageException {
        Log.v(TAG, "handleSessionEstablishmentMessage");

        final ECPublicKey theirPublicKey = parseHelloReq(payload);

        // Generate an EC key on the P-256 curve, and do ECDH to produce the shared secret
        final ECPublicKey ourPublicKey = generateSessionECDHKeyPair();
        generateSessionECDHSecret(theirPublicKey);

        // Send a response to allow the counterparty to perform ECDH as well
        try {
            mMessageSender.send(createHelloRsp(ourPublicKey));
        } catch (IOException e) {
            Log.e(TAG, "Failed to send HELLO_RSP; terminating session", e);
            onSessionError();
        }
    }

    // throw news SessionMessageException on any parsing or content failure within message
    @NonNull
    private ECPublicKey parseHelloReq(@NonNull byte[] message) throws SessionMessageException {
        if (message.length < ECDSAKeys.ENCODED_PUBLIC_KEY_LENGTH_BYTES) {
            throw new SessionMessageException("HELLO_REQ message smaller than expected");
        }

        final byte[] derSig;
        try {
            derSig = ECDSASignatures.convertECP256SignatureP1363ToDER(
                    message, message.length - ECDSASignatures.P256_P1363_SIGNATURE_LEN);
        } catch (IllegalArgumentException e) {
            throw new SessionMessageException("Invalid P1363 ECDSA signature", e);
        }

        final boolean verified;
        try {
            final Signature ecdsaSignature = Signature.getInstance("SHA256withECDSA");
            ecdsaSignature.initVerify(mAssociationPublicKey);
            ecdsaSignature.update(message, 0, ECDSAKeys.ENCODED_PUBLIC_KEY_LENGTH_BYTES);
            verified = ecdsaSignature.verify(derSig);
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Failed verifying signature of HELLO_REQ payload");
        }

        if (!verified) {
            throw new SessionMessageException("HELLO_REQ signature does not match payload");
        }

        final ECPublicKey otherPublicKey;
        try {
            otherPublicKey = ECDSAKeys.decodeP256PublicKey(message);
        } catch (UnsupportedOperationException e) {
            throw new SessionMessageException("Failed decoding HELLO_REQ payload as EC P-256 public key", e);
        }
        Log.v(TAG, "Received public key " + otherPublicKey.getW().getAffineX() + "/" +
                otherPublicKey.getW().getAffineY());
        return otherPublicKey;
    }

    @NonNull
    private static byte[] createHelloRsp(@NonNull ECPublicKey publicKey) {
        return ECDSAKeys.encodeP256PublicKey(publicKey);
    }
}
