package com.solana.mobilewalletadapter.common.ocms;

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class OffchainMessage {

    // Solana Off-chain signing domain constant
    // Hex encoding of "\xffsolana offchain"
    private static final byte[] SIGNING_DOMAIN = new byte[] {
            (byte)0xFF, 0x73, 0x6F, 0x6C, 0x61, 0x6E, 0x61, 0x20, 0x6F, 0x66, 0x66, 0x63, 0x68, 0x61, 0x69, 0x6E
    };

    private static final int SIGNER_LENGTH = 32;
    private static final int MAX_REQUIRED_SIGNERS = 255;
    private static final int MAX_CONTENT_LENGTH = 65452;
    private static final int MIN_MESSAGE_LENGTH = SIGNING_DOMAIN.length + 1 + 1 + SIGNER_LENGTH + 1;
    private static final int MAX_MESSAGE_LENGTH = SIGNING_DOMAIN.length + 1 +
            1 + MAX_REQUIRED_SIGNERS*SIGNER_LENGTH + MAX_CONTENT_LENGTH;

    private final int mMessageVersion;
    private final byte[][] mOrderedRequiredSigners;
    private final String mContent;

    private OffchainMessage(int messageVersion,
                            @NonNull @Size(min=1, max=MAX_REQUIRED_SIGNERS) byte[][] requiredSigners,
                            @NonNull @Size(min=1, max=MAX_CONTENT_LENGTH) String content) {
        assert(requiredSigners.length > 0 && requiredSigners.length <= MAX_REQUIRED_SIGNERS);
        assert(areSignersUnique(requiredSigners));
        assert(!content.isEmpty() && content.length() <= MAX_CONTENT_LENGTH);
        this.mMessageVersion = messageVersion;
        this.mContent = content;
        this.mOrderedRequiredSigners = Arrays.copyOf(requiredSigners, requiredSigners.length);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Arrays.sort(mOrderedRequiredSigners, Arrays::compareUnsigned);
        } else {
            Arrays.sort(mOrderedRequiredSigners, (a1, a2) -> {
                int n = Math.min(a1.length, a2.length);
                for (int i = 0; i < n; i++) {
                    int av1 = a1[i] & 0xFF;
                    int av2 = a2[i] & 0xFF;
                    if (av1 != av2) {
                        return av1 - av2;
                    }
                }
                return a1.length - a2.length;
            });
        }
    }

    public static OffchainMessage V1(@NonNull @Size(min=1, max=MAX_REQUIRED_SIGNERS) byte[][] requiredSigners,
                                     @NonNull @Size(min=1, max=MAX_CONTENT_LENGTH) String content) {
        return new OffchainMessage(1, requiredSigners, content);
    }

    public String getContent() {
        return mContent;
    }

    public int getVersion() { return mMessageVersion; }

    public byte[][] getRequiredSigners() { return mOrderedRequiredSigners; }

    public byte[] serialize() {
        final byte[] orderedSignersBytes = serializeOrderedRequiredSigners(mOrderedRequiredSigners);
        final byte[] contentBytes = mContent.getBytes(StandardCharsets.UTF_8);
        final byte[] offchainMessage = new byte[SIGNING_DOMAIN.length + 1 + orderedSignersBytes.length + contentBytes.length];
        System.arraycopy(SIGNING_DOMAIN, 0, offchainMessage, 0, SIGNING_DOMAIN.length);
        offchainMessage[SIGNING_DOMAIN.length] = (byte) mMessageVersion;
        System.arraycopy(orderedSignersBytes, 0, offchainMessage,
                SIGNING_DOMAIN.length + 1, orderedSignersBytes.length);
        System.arraycopy(contentBytes, 0, offchainMessage,
                SIGNING_DOMAIN.length + 1 + orderedSignersBytes.length, contentBytes.length);
        return offchainMessage;
    }

    public static OffchainMessage deserialize(byte[] message)
            throws IllegalArgumentException {
        if (message.length < MIN_MESSAGE_LENGTH || message.length > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message has invalid length");
        }

        ByteArrayInputStream input = new ByteArrayInputStream(message);
        byte[] signingDomain = new byte[SIGNING_DOMAIN.length];
        input.read(signingDomain, 0, signingDomain.length);
        if (!Arrays.equals(SIGNING_DOMAIN, signingDomain)) {
            throw new IllegalArgumentException("message has invalid signing domain");
        }

        int messageVersion = input.read();
        if (messageVersion != 1) {
            throw new IllegalArgumentException("message has invalid message version");
        }

        int requiredSignersCount = input.read();
        if (requiredSignersCount < 1 || requiredSignersCount > MAX_REQUIRED_SIGNERS) {
            throw new IllegalArgumentException("message has invalid required signer count");
        }
        byte[][] requiredSigners = new byte[requiredSignersCount][];
        for (int i = 0; i < requiredSignersCount; i++) {
            requiredSigners[i] = new byte[SIGNER_LENGTH];
            try {
                input.read(requiredSigners[i]);
            } catch(IOException e) {
                throw new IllegalArgumentException("message has invalid signers");
            }
        }
        if (!areSignersUnique(requiredSigners)) {
            throw new IllegalArgumentException("message has duplicate required signers");
        }

        final byte[] contentBytes = new byte[input.available()];
        final int read = input.read(contentBytes, 0, contentBytes.length);
        if (read < 1 || read > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("message has invalid content size");
        }
        final String content;
        try {
            CharsetDecoder charsetDecoder = StandardCharsets.UTF_8.newDecoder();
            CharBuffer decodedCharBuffer = charsetDecoder.decode(ByteBuffer.wrap(contentBytes));
            content = decodedCharBuffer.toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("message content is not UTF-8");
        }

        return new OffchainMessage(messageVersion, requiredSigners, content);
    }

    private byte[] serializeOrderedRequiredSigners(byte[][] orderedRequiredSigners) {
        byte[] serialized =  new byte[1 + orderedRequiredSigners.length*32];
        serialized[0] = (byte)orderedRequiredSigners.length;
        for(int i = 0; i < orderedRequiredSigners.length; i++) {
            System.arraycopy(orderedRequiredSigners[i], 0, serialized,
                    1 + i*32, orderedRequiredSigners[i].length);
        }
        return serialized;
    }

    private static boolean areSignersUnique(byte[][] signers) {
        for (int i = 0; i < signers.length - 1; i++) {
            for (int j = i + 1; j < signers.length; j++) {
                if (Arrays.equals(signers[i], signers[j])) return false;
            }
        }
        return true;
    }

    @NonNull
    @Override
    public String toString() {
        return "OffchainMessage{" +
                "version=" + mMessageVersion +
                "content=" + mContent +
                '}';
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj instanceof OffchainMessage &&
                this.mMessageVersion == ((OffchainMessage) obj).mMessageVersion &&
                Arrays.deepEquals(this.mOrderedRequiredSigners, ((OffchainMessage) obj).mOrderedRequiredSigners) &&
                this.mContent.equals(((OffchainMessage) obj).mContent);
    }
}
