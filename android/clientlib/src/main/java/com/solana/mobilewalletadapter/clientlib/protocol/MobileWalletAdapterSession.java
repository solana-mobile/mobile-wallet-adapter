/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;

public class MobileWalletAdapterSession extends MobileWalletAdapterSessionCommon {
    private static final String TAG = MobileWalletAdapterSession.class.getSimpleName();

    @NonNull
    private final KeyPair mAssociationKey;

    public MobileWalletAdapterSession(@NonNull MessageReceiver decryptedPayloadReceiver,
                                      @Nullable StateCallbacks stateCallbacks) {
        super(decryptedPayloadReceiver, stateCallbacks);
        mAssociationKey = generateECP256KeyPair();
    }

    // N.B. Does not need to be synchronized; it consumes only a final immutable object
    @NonNull
    public String encodeAssociationToken() {
        return encodeECP256PublicKeyToBase64((ECPublicKey) mAssociationKey.getPublic());
    }

    @Override
    protected void onReceiverConnected() {
        final ECPublicKey publicKey = generateSessionECDHKeyPair();
        final String request = createHelloReq(mAssociationKey, publicKey);
        try {
            mMessageSender.send(request.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "Failed to send HELLO_REQ; terminating session", e);
            onSessionError();
        }
    }

    @NonNull
    private static String createHelloReq(@NonNull KeyPair associationKeyPair,
                                         @NonNull ECPublicKey ourPublicKey) {
        final String ourPublicKeyBase64 = encodeECP256PublicKeyToBase64(ourPublicKey);

        final byte[] sig;
        try {
            final Signature ecdsaSignature = Signature.getInstance("ECDSA");
            ecdsaSignature.initSign(associationKeyPair.getPrivate());
            ecdsaSignature.update(ourPublicKeyBase64.getBytes(StandardCharsets.UTF_8));
            sig = ecdsaSignature.sign();
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Failed signing public key payload");
        }

        final String sigBase64 = Base64.encodeToString(sig,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

        final JSONObject o = new JSONObject();
        try {
            o.put(ProtocolContract.HELLO_MESSAGE_TYPE, ProtocolContract.HELLO_REQ_MESSAGE);
            o.put(ProtocolContract.HELLO_REQ_PUBLIC_KEY, encodeECP256PublicKeyToBase64(ourPublicKey));
            o.put(ProtocolContract.HELLO_REQ_PUBLIC_KEY_SIGNATURE, sigBase64);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed building HELLO_RSP", e);
        }
        return o.toString();
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
        Log.v(TAG, "parseHelloRsp");

        // Decode message as UTF-8 string
        final String s;
        try {
            s = decodeAsUtf8String(message);
        } catch (CharacterCodingException e) {
            throw new SessionMessageException("Failed decoding session establishment message", e);
        }

        // Parse string as HELLO_RSP JSON with an encoded public key payload
        final String qw;
        try {
            final JSONObject o = new JSONObject(s);
            final String m = o.getString(ProtocolContract.HELLO_MESSAGE_TYPE);
            if (!m.equals(ProtocolContract.HELLO_RSP_MESSAGE)) {
                throw new SessionMessageException("Unexpected message name: actual=" + m +
                        ", expected=" + ProtocolContract.HELLO_RSP_MESSAGE);
            }
            qw = o.getString(ProtocolContract.HELLO_RSP_PUBLIC_KEY);
        } catch (JSONException e) {
            throw new SessionMessageException("Failed interpreting message as HELLO_RSP: " + s, e);
        }

        final ECPublicKey otherPublicKey;
        try {
            otherPublicKey = decodeECP256PublicKeyFromBase64(qw);
        } catch (UnsupportedOperationException e) {
            throw new SessionMessageException("Failed creating EC public key for qw", e);
        }

        Log.v(TAG, "Received public key " + otherPublicKey.getW().getAffineX() + "/" +
                otherPublicKey.getW().getAffineY());
        return otherPublicKey;
    }
}
