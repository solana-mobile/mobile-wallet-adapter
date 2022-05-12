/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyType;
import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.walletlib.scenario.Scenario;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;

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
        mAssociationPublicKey = decodeAssociationToken(scenario.associationToken);
    }

    @NonNull
    private static ECPublicKey decodeAssociationToken(@NonNull String associationToken) {
        final JWK jwk;
        try {
            jwk = JWK.parse(decodeAsUtf8String(Base64.decode(associationToken, Base64.URL_SAFE)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed base64url-decoding the association token", e);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Failed UTF-8 decoding the association token", e);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Failed JWK parsing the association token", e);
        }

        if (!isJWKSuitableForP256SignatureVerification(jwk)) {
            throw new IllegalArgumentException("Association token does not meet key requirements");
        }

        try {
            return jwk.toECKey().toECPublicKey();
        } catch (JOSEException e) {
            throw new IllegalArgumentException("Erroring converting association token to ECPublicKey", e);
        }
    }

    protected static boolean isJWKSuitableForP256SignatureVerification(@NonNull JWK jwk) {
        return (jwk.getKeyType() == KeyType.EC &&
                jwk.getKeyOperations() != null &&
                jwk.getKeyOperations().size() == 1 &&
                jwk.getKeyOperations().contains(KeyOperation.VERIFY) &&
                jwk.toECKey().getCurve() == Curve.P_256 &&
                !jwk.isPrivate());
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
        final String response = createHelloRsp((ECPublicKey) ourPublicKey);
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

        // Parse string as HELLO_REQ JSON with a JWS payload, and check for expected fields
        final JWSObject jws;
        try {
            final JSONObject o = new JSONObject(s);
            final String m = o.getString(ProtocolContract.HELLO_MESSAGE_TYPE);
            if (!m.equals(ProtocolContract.HELLO_REQ_MESSAGE)) {
                throw new SessionMessageException("Unexpected message name: actual=" + m +
                        ", expected=" + ProtocolContract.HELLO_REQ_MESSAGE);
            }
            jws = JWSObject.parse(o.getString(ProtocolContract.HELLO_REQ_PUBLIC_KEY));
        } catch (JSONException e) {
            throw new SessionMessageException("Failed interpreting message as HELLO_REQ: " + s, e);
        } catch (ParseException e) {
            throw new SessionMessageException("Failed parsing JWT from HELLO_REQ", e);
        }

        if (jws.getHeader().getAlgorithm() != JWSAlgorithm.ES256) {
            throw new SessionMessageException("JWS algorithm invalid: actual=" +
                    jws.getHeader().getAlgorithm() + ", expected=" + JWSAlgorithm.ES256);
        }

        // Verify signature on qd
        final boolean verified;
        try {
            verified = jws.verify(new ECDSAVerifier(mAssociationPublicKey));
        } catch (JOSEException e) {
            throw new SessionMessageException("Error creating ECDSA verifier", e);
        } catch (IllegalStateException e) {
            throw new SessionMessageException("JWS is not signed, and thus cannot be verified", e);
        }
        if (!verified) {
            throw new SessionMessageException("JWS signature verification failed");
        }

        // Decode the JWK within qd
        final JWK jwk;
        try {
            jwk = JWK.parse(decodeAsUtf8String(jws.getPayload().toBytes()));
        } catch (CharacterCodingException e) {
            throw new SessionMessageException("Failed decoding JWS payload as a UTF-8 string", e);
        } catch (ParseException e) {
            throw new SessionMessageException("Failed parsing JWS payload as JWK", e);
        }

        // Verify JWK meets protocol specification
        if (!isJWKSuitableForP256ECDH(jwk)) {
            throw new SessionMessageException("JWK payload is not valid for ECDH");
        }

        final ECKey ecKey = jwk.toECKey();
        Log.d(TAG, "Received public key " + ecKey.getX() + "/" + ecKey.getY());

        try {
            return ecKey.toECPublicKey();
        } catch (JOSEException e) {
            throw new SessionMessageException("JWK is invalid", e);
        }
    }

    @NonNull
    private static String createHelloRsp(@NonNull ECPublicKey publicKey) {
        try {
            final JSONObject o = new JSONObject();
            o.put(ProtocolContract.HELLO_MESSAGE_TYPE, ProtocolContract.HELLO_RSP_MESSAGE);
            final JWK jwk = createJWKForECP256(publicKey, KeyOperation.DERIVE_KEY);
            o.put(ProtocolContract.HELLO_RSP_PUBLIC_KEY, new JSONObject(jwk.toJSONString()));
            return o.toString();
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed building HELLO_RSP", e);
        }
    }
}
