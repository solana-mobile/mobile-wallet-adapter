/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.common.protocol;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.impl.ConcatKDF;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public abstract class MobileWalletAdapterSessionCommon implements MessageReceiver, MessageSender {
    private static final String TAG = MobileWalletAdapterSessionCommon.class.getSimpleName();

    private static final int AES_IV_LENGTH_BYTES = 12;
    private static final int AES_TAG_LENGTH_BYTES = 16;

    @NonNull
    private final MessageReceiver mDecryptedPayloadReceiver;
    private final StateCallbacks mStateCallbacks;

    protected MessageSender mMessageSender;
    @NonNull
    private State mState = State.WAITING_FOR_CONNECTION;
    private KeyPair mECDHKeypair;
    private SecretKey mCachedEncryptionKey;

    protected MobileWalletAdapterSessionCommon(@NonNull MessageReceiver decryptedPayloadReceiver,
                                               @Nullable StateCallbacks stateCallbacks) {
        mDecryptedPayloadReceiver = decryptedPayloadReceiver;
        mStateCallbacks = stateCallbacks;
    }

    @Override
    public synchronized void receiverConnected(@NonNull MessageSender messageSender) {
        Log.v(TAG, "receiverConnected");
        assert(mState == State.WAITING_FOR_CONNECTION);
        mState = State.SESSION_ESTABLISHMENT;
        mMessageSender = messageSender;
        onReceiverConnected();
    }

    protected void onReceiverConnected() {}

    @Override
    public synchronized void receiverDisconnected() {
        Log.v(TAG, "receiverDisconnected");
        assert(mState != State.WAITING_FOR_CONNECTION);

        // N.B. mState can be State.CLOSED if onSessionError was previously invoked
        if (mState != State.CLOSED) {
            doClose();
            Log.i(TAG, "mobile-wallet-adapter session closed");

            if (mStateCallbacks != null) {
                mStateCallbacks.onSessionClosed();
            }
        }
    }

    protected void onSessionError() {
        Log.v(TAG, "onSessionError");
        assert(mState == State.SESSION_ESTABLISHMENT || mState == State.ENCRYPTED_SESSION);

        doClose();
        Log.w(TAG, "mobile-wallet-adapter session closed due to error");

        if (mStateCallbacks != null) {
            mStateCallbacks.onSessionError();
        }
    }

    private void doClose() {
        mState = State.CLOSED;
        mMessageSender = null;
        mECDHKeypair = null;
        mCachedEncryptionKey = null;
        mDecryptedPayloadReceiver.receiverDisconnected();
    }

    @Override
    public synchronized void receiverMessageReceived(@NonNull byte[] payload) {
        Log.v(TAG, "receiverMessageReceived: size=" + payload.length);

        // ignore empty PING messages in all states
        if (payload.length == 0) {
            return;
        }

        try {
            switch (mState) {
                case WAITING_FOR_CONNECTION:
                    throw new IllegalStateException("Received a message before connection");
                case SESSION_ESTABLISHMENT:
                    handleSessionEstablishmentMessage(payload);
                    break;
                case ENCRYPTED_SESSION:
                    handleEncryptedSessionPayload(payload);
                    break;
                case CLOSED:
                    Log.w(TAG, "message received after closed, ignoring");
                    break;
            }
        } catch (SessionMessageException e) {
            Log.e(TAG, "Invalid message received; terminating session", e);
            onSessionError();
        }
    }

    protected abstract void handleSessionEstablishmentMessage(@NonNull byte[] payload)
            throws SessionMessageException;

    private void handleEncryptedSessionPayload(@NonNull byte[] encryptedPayload)
            throws SessionMessageException {
        Log.v(TAG, "handleEncryptedSessionMessage");
        final byte[] payload = decryptSessionPayload(encryptedPayload);
        mDecryptedPayloadReceiver.receiverMessageReceived(payload);
    }

    @Override
    public void send(@NonNull byte[] message) throws IOException {
        final byte[] encryptedPayload;

        Log.v(TAG, "send");

        synchronized (this) {
            if (mState != State.ENCRYPTED_SESSION) {
                throw new IOException("Cannot send in " + mState);
            }

            encryptedPayload = encryptSessionPayload(message);
        }

        // Don't hold lock when calling into sender; it could lead to lock-ordering deadlocks.
        mMessageSender.send(encryptedPayload);
    }

    @NonNull
    protected byte[] encryptSessionPayload(@NonNull byte[] payload) {
        if (mCachedEncryptionKey == null) {
            throw new IllegalStateException("Cannot decrypt, no session key has been established");
        }

        try {
            final Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            final byte[] iv = new byte[AES_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(
                    AES_TAG_LENGTH_BYTES * 8, iv);
            aesCipher.init(Cipher.ENCRYPT_MODE, mCachedEncryptionKey, gcmParameterSpec);
            final byte[] ciphertext = Arrays.copyOf(iv, AES_IV_LENGTH_BYTES +
                    aesCipher.getOutputSize(payload.length));
            aesCipher.doFinal(payload, 0, payload.length, ciphertext, AES_IV_LENGTH_BYTES);
            return ciphertext;
        } catch (InvalidAlgorithmParameterException | NoSuchPaddingException |
                IllegalBlockSizeException | ShortBufferException | NoSuchAlgorithmException |
                BadPaddingException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Error encrypting session payload", e);
        }
    }

    @NonNull
    protected byte[] decryptSessionPayload(@NonNull byte[] payload) throws SessionMessageException {
        if (mCachedEncryptionKey == null) {
            throw new IllegalStateException("Cannot decrypt, no session key has been established");
        }

        try {
            final Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(
                    AES_TAG_LENGTH_BYTES * 8, payload, 0, AES_IV_LENGTH_BYTES);
            aesCipher.init(Cipher.DECRYPT_MODE, mCachedEncryptionKey, gcmParameterSpec);
            return aesCipher.doFinal(payload, AES_IV_LENGTH_BYTES,
                    payload.length - AES_IV_LENGTH_BYTES);
        } catch (InvalidAlgorithmParameterException | NoSuchPaddingException |
                IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException |
                InvalidKeyException e) {
            throw new SessionMessageException("Failed decrypting payload", e);
        }
    }

    @NonNull
    private static SecretKey createEncryptionKey(@NonNull byte[] ecdhSecret) {
        try {
            return new ConcatKDF("SHA-256").deriveKey(
                    new SecretKeySpec(ecdhSecret, "AES"),
                    EncryptionMethod.A128GCM.cekBitLength(),
                    EncryptionMethod.A128GCM.getName().getBytes(StandardCharsets.UTF_8),
                    null,
                    null,
                    ConcatKDF.encodeIntData(EncryptionMethod.A128GCM.cekBitLength()),
                    null);
        } catch (JOSEException e) {
            throw new UnsupportedOperationException("ConcatKDF key derivation failed", e);
        }
    }

    @NonNull
    protected static KeyPair generateECP256KeyPair() {
        try {
            final AlgorithmParameters algParams = AlgorithmParameters.getInstance("EC");
            algParams.init(new ECGenParameterSpec("secp256r1"));
            final ECParameterSpec ecParameterSpec = algParams.getParameterSpec(ECParameterSpec.class);

            final KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(ecParameterSpec);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException |
                InvalidAlgorithmParameterException e) {
            throw new UnsupportedOperationException("Failed generating an EC P-256 keypair for ECDH", e);
        }
    }

    @NonNull
    protected ECPublicKey generateSessionECDHKeyPair() {
        Log.v(TAG, "generateSessionECDHKeyPair");

        if (mState != State.SESSION_ESTABLISHMENT) {
            throw new IllegalStateException("Incorrect state for generating session ECDH keypair");
        }

        mECDHKeypair = generateECP256KeyPair();
        return (ECPublicKey) mECDHKeypair.getPublic();
    }

    protected void generateSessionECDHSecret(@NonNull ECPublicKey otherPublicKey) {
        Log.v(TAG, "generateSessionECDHSecret");

        if (mState != State.SESSION_ESTABLISHMENT) {
            throw new IllegalStateException("Incorrect state for generating session ECDH secret");
        }

        try {
            final KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(mECDHKeypair.getPrivate());
            keyAgreement.doPhase(otherPublicKey, true);
            final byte[] ecdhSecret = keyAgreement.generateSecret();
            mCachedEncryptionKey = createEncryptionKey(ecdhSecret);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Failed generating an ECDH secret", e);
        }

        mState = State.ENCRYPTED_SESSION;

        Log.i(TAG, "Encrypted session established");

        mDecryptedPayloadReceiver.receiverConnected(this);

        if (mStateCallbacks != null) {
            mStateCallbacks.onSessionEstablished();
        }
    }

    @NonNull
    protected static String encodeECP256PublicKeyToBase64(@NonNull ECPublicKey ecPublicKey) {
        final ECPoint w = ecPublicKey.getW();
        // NOTE: either x or y could be 33 bytes long, due to BigInteger always including a sign bit
        // in the output. Discard it; we are only interested in the unsigned magnitude.
        final byte[] x = w.getAffineX().toByteArray();
        final byte[] y = w.getAffineY().toByteArray();
        final byte[] encodedPublicKey = new byte[65];
        encodedPublicKey[0] = 0x04; // non-compressed public key
        final int xLen = Math.min(x.length, 32);
        final int yLen = Math.min(y.length, 32);
        System.arraycopy(x, x.length - xLen, encodedPublicKey, 33 - xLen, xLen);
        System.arraycopy(y, y.length - yLen, encodedPublicKey, 65 - yLen, yLen);
        return Base64.encodeToString(encodedPublicKey,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    @NonNull
    protected static ECPublicKey decodeECP256PublicKeyFromBase64(@NonNull String ecPublicKeyBase64) {
        final byte[] encodedPublicKey = Base64.decode(ecPublicKeyBase64, Base64.URL_SAFE);
        if (encodedPublicKey.length != 65 || encodedPublicKey[0] != 0x04) {
            throw new IllegalArgumentException("input is not a base64-encoded EC P-256 public key");
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

    @NonNull
    protected static String decodeAsUtf8String(@NonNull byte[] b) throws CharacterCodingException {
        final CharsetDecoder utf8Dec = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        final ByteBuffer bb = ByteBuffer.wrap(b);
        return utf8Dec.decode(bb).toString();
    }

    protected static class SessionMessageException extends Exception {
        public SessionMessageException() {}
        public SessionMessageException(@Nullable String message) { super(message); }
        public SessionMessageException(@Nullable String message, @Nullable Throwable cause) { super(message, cause); }
    }

    private enum State {
        WAITING_FOR_CONNECTION, SESSION_ESTABLISHMENT, ENCRYPTED_SESSION, CLOSED
    }

    public interface StateCallbacks {
        /** Session has been fully established, and is ready for use */
        void onSessionEstablished();

        /**
         * Session has been closed normally, and should no longer be used.
         * <p/>Note that there is no guarantee {@link #onSessionEstablished()} will have been
         * invoked when this is called; a session can be closed during establishment by either
         * party.
         */
        void onSessionClosed();

        /**
         * An unrecoverable error occurred during session establishment or operation. This session
         * should no longer be used.
         * <p/>Note that this is a terminal indication; {@link #onSessionClosed()} will not be
         * invoked for this session.
         */
        void onSessionError();
    }
}
