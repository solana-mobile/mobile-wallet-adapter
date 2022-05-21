/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
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
                                      @Nullable StateCallbacks stateCallbacks,
                                      @Nullable PayloadEncryptionMethod payloadEncryptionMethod,
                                      boolean deflatePayload) {
        super(decryptedPayloadReceiver, stateCallbacks, payloadEncryptionMethod, deflatePayload);
        mScenario = scenario;
        mAssociationPublicKey = decodeECP256PublicKeyFromBase64(scenario.associationToken);
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
        final String response = createHelloRsp(ourPublicKey);
        try {
            mMessageSender.send(response.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "Failed to send HELLO_RSP; terminating session", e);
            onSessionError();
        }
    }

    // throw news SessionMessageException on any parsing or content failure within message
    @NonNull
    private ECPublicKey parseHelloReq(@NonNull byte[] message) throws SessionMessageException {
        Log.v(TAG, "parseHelloReq");

        // Decode message as UTF-8 string
        final String s;
        try {
            s = decodeAsUtf8String(message);
        } catch (CharacterCodingException e) {
            throw new SessionMessageException("Failed decoding session establishment message", e);
        }

        // Parse string as HELLO_REQ JSON with an encoded public key payload and a corresponding
        // ECDSA signature
        final String otherPublicKeyBase64;
        final String sigBase64;
        try {
            final JSONObject o = new JSONObject(s);
            final String m = o.getString(ProtocolContract.HELLO_MESSAGE_TYPE);
            if (!m.equals(ProtocolContract.HELLO_REQ_MESSAGE)) {
                throw new SessionMessageException("Unexpected message name: actual=" + m +
                        ", expected=" + ProtocolContract.HELLO_REQ_MESSAGE);
            }
            otherPublicKeyBase64 = o.getString(ProtocolContract.HELLO_REQ_PUBLIC_KEY);
            sigBase64 = o.getString(ProtocolContract.HELLO_REQ_PUBLIC_KEY_SIGNATURE);
        } catch (JSONException e) {
            throw new SessionMessageException("Failed interpreting message as HELLO_REQ: " + s, e);
        }

        // Decode signature and verify otherPublicKeyBase64
        final byte[] sig = Base64.decode(sigBase64, Base64.URL_SAFE);
        final boolean verified;
        try {
            final Signature ecdsaSignature = Signature.getInstance("ECDSA");
            ecdsaSignature.initVerify(mAssociationPublicKey);
            ecdsaSignature.update(otherPublicKeyBase64.getBytes(StandardCharsets.UTF_8));
            verified = ecdsaSignature.verify(sig);
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new SessionMessageException("signature verification failed", e);
        }
        if (!verified) {
            throw new SessionMessageException("signature verification failed");
        }

        // Decode other public key
        final ECPublicKey otherPublicKey = decodeECP256PublicKeyFromBase64(otherPublicKeyBase64);
        Log.v(TAG, "Received public key " + otherPublicKey.getW().getAffineX() + "/" +
                otherPublicKey.getW().getAffineY());
        return otherPublicKey;
    }

    @NonNull
    private static String createHelloRsp(@NonNull ECPublicKey publicKey) {
        final JSONObject o = new JSONObject();
        try {
            o.put(ProtocolContract.HELLO_MESSAGE_TYPE, ProtocolContract.HELLO_RSP_MESSAGE);
            o.put(ProtocolContract.HELLO_RSP_PUBLIC_KEY, encodeECP256PublicKeyToBase64(publicKey));
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed building HELLO_RSP", e);
        }
        return o.toString();
    }
}
