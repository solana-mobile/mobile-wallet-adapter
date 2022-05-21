/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyOperation;
import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;

public class MobileWalletAdapterSession extends MobileWalletAdapterSessionCommon {
    private static final String TAG = MobileWalletAdapterSession.class.getSimpleName();

    @NonNull
    private final KeyPair mAssociationKey;

    public MobileWalletAdapterSession(@NonNull MessageReceiver decryptedPayloadReceiver,
                                      @Nullable StateCallbacks stateCallbacks,
                                      @Nullable PayloadEncryptionMethod payloadEncryptionMethod,
                                      boolean deflatePayload) {
        super(decryptedPayloadReceiver, stateCallbacks, payloadEncryptionMethod, deflatePayload);
        mAssociationKey = generateECP256KeyPair();
    }

    // N.B. Does not need to be synchronized; it consumes only a final immutable object
    @NonNull
    public String encodeAssociationToken() {
        try {
            return encodeECP256PublicKeyToBase64(KeyFactory.getInstance("EC")
                    .getKeySpec(mAssociationKey.getPublic(), ECPublicKeySpec.class));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new UnsupportedOperationException("Error converting association public key to keyspec", e);
        }
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
        try {
            final JSONObject o = new JSONObject();
            o.put(ProtocolContract.HELLO_MESSAGE_TYPE, ProtocolContract.HELLO_REQ_MESSAGE);
            final JWK jwk = createJWKForECP256(ourPublicKey, KeyOperation.DERIVE_KEY);
            final JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.ES256);
            final JWSObject jws = new JWSObject(jwsHeader,
                    new Payload(jwk.toJSONString().getBytes(StandardCharsets.UTF_8)));
            final JWSSigner jwsSigner =
                    new ECDSASigner((ECPrivateKey) associationKeyPair.getPrivate());
            jws.sign(jwsSigner);
            o.put(ProtocolContract.HELLO_REQ_PUBLIC_KEY, jws.serialize());
            return o.toString();
        } catch (JOSEException | JSONException e) {
            throw new UnsupportedOperationException("Failed building HELLO_RSP", e);
        }
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

        // Parse string as HELLO_RSP JSON with a JWK payload, and check for expected fields
        final JWK jwk;
        try {
            final JSONObject o = new JSONObject(s);
            final String m = o.getString(ProtocolContract.HELLO_MESSAGE_TYPE);
            if (!m.equals(ProtocolContract.HELLO_RSP_MESSAGE)) {
                throw new SessionMessageException("Unexpected message name: actual=" + m +
                        ", expected=" + ProtocolContract.HELLO_RSP_MESSAGE);
            }
            jwk = JWK.parse(o.getString(ProtocolContract.HELLO_RSP_PUBLIC_KEY));
        } catch (JSONException e) {
            throw new SessionMessageException("Failed interpreting message as HELLO_RSP: " + s, e);
        } catch (ParseException e) {
            throw new SessionMessageException("Failed parsing JWT from HELLO_RSP", e);
        }

        // Verify JWK meets protocol specification
        if (!isJWKSuitableForP256ECDH(jwk)) {
            throw new SessionMessageException("JWK payload is not valid for ECDH");
        }

        final ECKey ecKey = jwk.toECKey();
        Log.v(TAG, "Received public key " + ecKey.getX() + "/" + ecKey.getY());

        try {
            return ecKey.toECPublicKey();
        } catch (JOSEException e) {
            throw new SessionMessageException("JWK is invalid", e);
        }
    }
}
