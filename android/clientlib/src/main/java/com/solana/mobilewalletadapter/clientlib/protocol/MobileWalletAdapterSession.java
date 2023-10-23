/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.crypto.ECDSAKeys;
import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MobileWalletAdapterSessionCommon;
import com.solana.mobilewalletadapter.common.crypto.ECDSASignatures;
import com.solana.mobilewalletadapter.common.protocol.SessionProperties;

import org.json.JSONException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Set;

public class MobileWalletAdapterSession extends MobileWalletAdapterSessionCommon {
    private static final String TAG = MobileWalletAdapterSession.class.getSimpleName();

    @NonNull
    private final KeyPair mAssociationKey;

    @NonNull
    private final Set<SessionProperties.ProtocolVersion> mSupportedProtocolVersions;

    @Nullable
    private SessionProperties mSessionProperties;

    public MobileWalletAdapterSession(@NonNull MessageReceiver decryptedPayloadReceiver,
                                      @Nullable StateCallbacks stateCallbacks) {
        this(decryptedPayloadReceiver, stateCallbacks,
                Set.of(SessionProperties.ProtocolVersion.LEGACY, SessionProperties.ProtocolVersion.V1));
    }

    protected MobileWalletAdapterSession(@NonNull MessageReceiver decryptedPayloadReceiver,
                                         @Nullable StateCallbacks stateCallbacks,
                                         @NonNull Set<SessionProperties.ProtocolVersion> supportedProtocolVersions) {
        super(decryptedPayloadReceiver, stateCallbacks);
        mAssociationKey = generateECP256KeyPair();
        mSupportedProtocolVersions = supportedProtocolVersions;
        mSessionProperties = null;
    }

    public Set<SessionProperties.ProtocolVersion> getSupportedProtocolVersions() { return mSupportedProtocolVersions; }

    @NonNull
    @Override
    protected ECPublicKey getAssociationPublicKey() {
        return (ECPublicKey) mAssociationKey.getPublic();
    }

    @NonNull
    @Override
    public SessionProperties getSessionProperties() {
        if (mSessionProperties == null)
            throw new IllegalStateException("session properties unknown, no session has been established");
        return mSessionProperties;
    }

    // N.B. Does not need to be synchronized; it consumes only a final immutable object
    @NonNull
    public byte[] getEncodedAssociationPublicKey() {
        return ECDSAKeys.encodeP256PublicKey((ECPublicKey) mAssociationKey.getPublic());
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
        final byte[] ourPublicKeyEncoded = ECDSAKeys.encodeP256PublicKey(ourPublicKey);

        final byte[] sig;
        try {
            final Signature ecdsaSignature = Signature.getInstance("SHA256withECDSA");
            ecdsaSignature.initSign(associationKeyPair.getPrivate());
            ecdsaSignature.update(ourPublicKeyEncoded);
            sig = ecdsaSignature.sign();
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Failed signing HELLO_REQ public key payload");
        }

        final byte[] p1363Sig;
        try {
            p1363Sig = ECDSASignatures.convertECP256SignatureDERtoP1363(sig, 0);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException("Error converting DER ECDSA signature to P1363", e);
        }

        final byte[] concatenated = Arrays.copyOf(ourPublicKeyEncoded,
                ourPublicKeyEncoded.length + p1363Sig.length);
        System.arraycopy(p1363Sig, 0, concatenated, ourPublicKeyEncoded.length, p1363Sig.length);
        return concatenated;
    }

    @Override
    protected void handleSessionEstablishmentMessage(@NonNull byte[] payload)
            throws SessionMessageException {
        Log.v(TAG, "handleSessionEstablishmentMessage");

        final ECPublicKey theirPublicKey = parseHelloRsp(payload);
        generateSessionECDHSecret(theirPublicKey);

        SessionProperties sessionProperties = new SessionProperties(SessionProperties.ProtocolVersion.LEGACY);
        try {
            if (mSupportedProtocolVersions.contains(SessionProperties.ProtocolVersion.V1)) {
                byte[] encryptedSessionProperties =
                        Arrays.copyOfRange(payload, ECDSAKeys.ENCODED_PUBLIC_KEY_LENGTH_BYTES, payload.length);
                sessionProperties = parseSessionProps(encryptedSessionProperties);
            }
        } catch (IndexOutOfBoundsException ignored) {
            Log.w(TAG, "could not parse session properties, falling back on legacy session");
        } finally {
            mSessionProperties = sessionProperties;
        }

        doSessionEstablished();
    }

    @NonNull
    private ECPublicKey parseHelloRsp(@NonNull byte[] message) throws SessionMessageException {
        final ECPublicKey otherPublicKey;
        try {
            otherPublicKey = ECDSAKeys.decodeP256PublicKey(message);
        } catch (UnsupportedOperationException e) {
            throw new SessionMessageException("Failed creating EC public key from HELLO_RSP", e);
        }

        Log.v(TAG, "Received public key " + otherPublicKey.getW().getAffineX() + "/" +
                otherPublicKey.getW().getAffineY());
        return otherPublicKey;
    }

    @NonNull
    private SessionProperties parseSessionProps(@NonNull byte[] message) throws SessionMessageException {
        final SessionProperties properties;
        try {
            byte[] sessionProps = decryptSessionPayload(message);
            properties = SessionProperties.deserialize(sessionProps);
        } catch (JSONException e) {
            throw new SessionMessageException("Failed to parse SESSION_PROPS", e);
        }

        Log.v(TAG, "Received session properties: version = " + properties.protocolVersion);

        return properties;
    }
}
