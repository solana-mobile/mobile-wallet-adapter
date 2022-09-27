/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common.protocol;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.crypto.ECDSAKeys;
import com.solana.mobilewalletadapter.common.crypto.HKDF;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
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

    private static final int SEQ_NUM_LENGTH_BYTES = 4;
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
    private int mSeqNumberTx;
    private int mSeqNumberRx;

    protected MobileWalletAdapterSessionCommon(@NonNull MessageReceiver decryptedPayloadReceiver,
                                               @Nullable StateCallbacks stateCallbacks) {
        mDecryptedPayloadReceiver = decryptedPayloadReceiver;
        mStateCallbacks = stateCallbacks;
    }

    @NonNull
    protected abstract ECPublicKey getAssociationPublicKey();

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

        final byte[] seqNum = new byte[SEQ_NUM_LENGTH_BYTES];
        ByteBuffer.wrap(seqNum).putInt(++mSeqNumberTx); // Big-endian

        try {
            final Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            final byte[] iv = new byte[AES_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(
                    AES_TAG_LENGTH_BYTES * 8, iv);
            aesCipher.init(Cipher.ENCRYPT_MODE, mCachedEncryptionKey, gcmParameterSpec);
            aesCipher.updateAAD(seqNum, 0, SEQ_NUM_LENGTH_BYTES);
            final byte[] encryptedMessage = Arrays.copyOf(seqNum, SEQ_NUM_LENGTH_BYTES +
                    AES_IV_LENGTH_BYTES + aesCipher.getOutputSize(payload.length));
            System.arraycopy(iv, 0, encryptedMessage, SEQ_NUM_LENGTH_BYTES, AES_IV_LENGTH_BYTES);
            aesCipher.doFinal(payload, 0, payload.length, encryptedMessage,
                    SEQ_NUM_LENGTH_BYTES + AES_IV_LENGTH_BYTES);
            return encryptedMessage;
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

        final int seqNum = ByteBuffer.wrap(payload, 0, SEQ_NUM_LENGTH_BYTES).getInt(); // Big-endian
        if (seqNum != (mSeqNumberRx + 1)) {
            throw new SessionMessageException("Encrypted messages has invalid sequence number");
        }
        mSeqNumberRx = seqNum;

        try {
            final Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(
                    AES_TAG_LENGTH_BYTES * 8, payload, SEQ_NUM_LENGTH_BYTES, AES_IV_LENGTH_BYTES);
            aesCipher.init(Cipher.DECRYPT_MODE, mCachedEncryptionKey, gcmParameterSpec);
            aesCipher.updateAAD(payload, 0, SEQ_NUM_LENGTH_BYTES);
            return aesCipher.doFinal(payload, SEQ_NUM_LENGTH_BYTES + AES_IV_LENGTH_BYTES,
                    payload.length - SEQ_NUM_LENGTH_BYTES - AES_IV_LENGTH_BYTES);
        } catch (InvalidAlgorithmParameterException | NoSuchPaddingException |
                IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException |
                InvalidKeyException e) {
            throw new SessionMessageException("Failed decrypting payload", e);
        }
    }

    @NonNull
    private static SecretKey createEncryptionKey(@NonNull byte[] ecdhSecret,
                                                 @NonNull ECPublicKey associationPublicKey) {
        final byte[] salt = ECDSAKeys.encodeP256PublicKey(associationPublicKey);
        final byte[] aes128KeyMaterial = HKDF.hkdfSHA256L16(ecdhSecret, salt);
        return new SecretKeySpec(aes128KeyMaterial, "AES");
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
            mCachedEncryptionKey = createEncryptionKey(ecdhSecret, getAssociationPublicKey());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Failed generating an ECDH secret", e);
        }

        mSeqNumberTx = 0;
        mSeqNumberRx = 0;

        mState = State.ENCRYPTED_SESSION;

        Log.i(TAG, "Encrypted session established");

        mDecryptedPayloadReceiver.receiverConnected(this);

        if (mStateCallbacks != null) {
            mStateCallbacks.onSessionEstablished();
        }
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
         * <p>Note that there is no guarantee {@link #onSessionEstablished()} will have been
         * invoked when this is called; a session can be closed during establishment by either
         * party.</p>
         */
        void onSessionClosed();

        /**
         * An unrecoverable error occurred during session establishment or operation. This session
         * should no longer be used.
         * <p>Note that this is a terminal indication; {@link #onSessionClosed()} will not be
         * invoked for this session.</p>
         */
        void onSessionError();
    }
}
