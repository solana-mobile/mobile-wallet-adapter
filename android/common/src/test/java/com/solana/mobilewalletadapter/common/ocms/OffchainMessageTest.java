package com.solana.mobilewalletadapter.common.ocms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class OffchainMessageTest {

    private static final byte[] SIGNING_DOMAIN = new byte[] {
            (byte) 0xff, 0x73, 0x6f, 0x6c, 0x61, 0x6e, 0x61, 0x20,
            0x6f, 0x66, 0x66, 0x63, 0x68, 0x61, 0x69, 0x6e,
    };

    private static final byte[] SIGNER_A = new byte[] {
            0x0c, (byte) 0xfe, 0x2c, (byte) 0xc9, 0x52, 0x55, 0x0e, (byte) 0x94,
            (byte) 0xc7, 0x25, 0x63, (byte) 0x9a, 0x4b, (byte) 0xd1, 0x1d, 0x4e,
            (byte) 0xa5, (byte) 0xa6, 0x38, 0x36, 0x51, (byte) 0xc3, 0x08, (byte) 0xb7,
            0x18, (byte) 0xc3, (byte) 0xae, (byte) 0xf2, (byte) 0x86, (byte) 0xbc, (byte) 0xa1, (byte) 0xaf,
    };

    private static final byte[] SIGNER_B = new byte[] {
            0x0c, (byte) 0xfe, 0x2c, (byte) 0xc9, 0x52, 0x5c, (byte) 0x95, (byte) 0xef,
            (byte) 0xb9, 0x72, (byte) 0xc0, (byte) 0xc5, (byte) 0xb7, (byte) 0xae, 0x0f, (byte) 0xd5,
            0x20, (byte) 0xd9, 0x7e, (byte) 0x94, (byte) 0x8f, (byte) 0xd8, (byte) 0xbb, 0x2c,
            0x10, (byte) 0xa1, 0x01, 0x02, (byte) 0xce, (byte) 0x98, (byte) 0xb3, (byte) 0xa6,
    };

    @Test
    public void v1ConstructorThrowsWhenNoRequiredSigners() {
        // given
        final byte[][] requiredSigners = new byte[][] {};
        final String content = "Hello\nworld";

        // then
        assertThrows(AssertionError.class,
                () -> OffchainMessage.V1(requiredSigners, content));
    }

    @Test
    public void v1ConstructorThrowsWhenContentIsEmpty() {
        // given
        final byte[][] requiredSigners = new byte[][] { SIGNER_A, SIGNER_B };
        final String content = "";

        // then
        assertThrows(AssertionError.class,
                () -> OffchainMessage.V1(requiredSigners, content));
    }

    @Test
    public void v1ConstructorThrowsWhenSignersAreDuplicated() {
        // given
        final byte[][] requiredSigners = new byte[][] { SIGNER_A, SIGNER_A };
        final String content = "Hello\nworld";

        // then
        assertThrows(AssertionError.class, () -> OffchainMessage.V1(requiredSigners, content));
    }

    //region ENCODE
    @Test
    public void serializeAsciiContentWithSingleSigner() {
        // given
        final byte[][] requiredSigners = new byte[][] { SIGNER_A };
        final String content = "Hello\nworld";
        final byte[] expected = concat(
                SIGNING_DOMAIN,
                new byte[] { 0x01 },        // version
                new byte[] { 0x01 },        // signer count
                SIGNER_A,
                content.getBytes(StandardCharsets.UTF_8)
        );

        // when
        final byte[] actual = OffchainMessage.V1(requiredSigners, content).serialize();

        // then
        assertArrayEquals(expected, actual);
    }

    @Test
    public void serializeAsciiContentWithTwoSigners() {
        // given
        final byte[][] requiredSigners = new byte[][] { SIGNER_A, SIGNER_B };
        final String content = "Hello\nworld";
        final byte[] expected = concat(
                SIGNING_DOMAIN,
                new byte[] { 0x01 },        // version
                new byte[] { 0x02 },        // signer count
                SIGNER_A,
                SIGNER_B,
                content.getBytes(StandardCharsets.UTF_8)
        );

        // when
        final byte[] actual = OffchainMessage.V1(requiredSigners, content).serialize();

        // then
        assertArrayEquals(expected, actual);
    }

    @Test
    public void serializeUtf8ContentWithTwoSigners() {
        // given
        final byte[][] requiredSigners = new byte[][] { SIGNER_A, SIGNER_B };
        final String content = "✌🏿cool";
        final byte[] expected = concat(
                SIGNING_DOMAIN,
                new byte[] { 0x01 },        // version
                new byte[] { 0x02 },        // signer count
                SIGNER_A,
                SIGNER_B,
                content.getBytes(StandardCharsets.UTF_8)
        );

        // when
        final byte[] actual = OffchainMessage.V1(requiredSigners, content).serialize();

        // then
        assertArrayEquals(expected, actual);
    }

    @Test
    public void serializeSortsSignersInLexicographicalOrder() {
        // given
        final byte[][] requiredSigners = new byte[][] { SIGNER_B, SIGNER_A };   // unsorted input
        final String content = "Hello\nworld";
        final byte[] expected = concat(
                SIGNING_DOMAIN,
                new byte[] { 0x01 },        // version
                new byte[] { 0x02 },        // signer count
                SIGNER_A,                   // sorted output: A before B
                SIGNER_B,
                content.getBytes(StandardCharsets.UTF_8)
        );

        // when
        final byte[] actual = OffchainMessage.V1(requiredSigners, content).serialize();

        // then
        assertArrayEquals(expected, actual);
    }
    //endregion

    //region DECODE
    @Test
    public void deserializeWellFormedMessageWithTwoSigners() {
        // given
        final String content = "Hello\nworld";
        final byte[] message = concat(
                SIGNING_DOMAIN,
                new byte[] { 0x01 },        // version
                new byte[] { 0x02 },        // signer count
                SIGNER_A,
                SIGNER_B,
                content.getBytes(StandardCharsets.UTF_8)
        );
        final OffchainMessage expected = OffchainMessage.V1(
                new byte[][] { SIGNER_A, SIGNER_B }, content);

        // when
        final OffchainMessage actual = OffchainMessage.deserialize(message);

        // then
        assertEquals(expected, actual);
    }

    @Test
    public void deserializeThrowsOnMalformedSigningDomain() {
        // given
        final byte[] malformedDomain = SIGNING_DOMAIN.clone();
        malformedDomain[0] = 0x00;      // not 0xff
        final byte[] message = concat(
                malformedDomain,
                new byte[] { 0x01 },        // version
                new byte[] { 0x02 },        // signer count
                SIGNER_A,
                SIGNER_B,
                "Hello\nworld".getBytes(StandardCharsets.UTF_8)
        );

        // then
        assertThrows(IllegalArgumentException.class, () -> OffchainMessage.deserialize(message));
    }

    @Test
    public void deserializeThrowsOnUnexpectedVersion() {
        // given
        final byte[] message = concat(
                SIGNING_DOMAIN,
                new byte[] { 0x00 },        // version (0, not 1)
                new byte[] { 0x02 },        // signer count
                SIGNER_A,
                SIGNER_B,
                "Hello\nworld".getBytes(StandardCharsets.UTF_8)
        );

        // then
        assertThrows(IllegalArgumentException.class, () -> OffchainMessage.deserialize(message));
    }

    @Test
    public void deserializeThrowsOnUnsupportedVersion() {
        // given
        final byte[] message = concat(
                SIGNING_DOMAIN,
                new byte[] { (byte) 0xff },  // version (255)
                new byte[] { 0x02 },        // signer count
                SIGNER_A,
                SIGNER_B,
                "Hello\nworld".getBytes(StandardCharsets.UTF_8)
        );

        // then
        assertThrows(IllegalArgumentException.class, () -> OffchainMessage.deserialize(message));
    }

    @Test
    public void deserializeThrowsOnDuplicateSigners() {
        // given
        final byte[] message = concat(
                SIGNING_DOMAIN,
                new byte[] { 0x01 },        // version
                new byte[] { 0x02 },        // signer count
                SIGNER_A,
                SIGNER_A,                   // duplicate
                "Hello\nworld".getBytes(StandardCharsets.UTF_8)
        );

        // then
        assertThrows(IllegalArgumentException.class, () -> OffchainMessage.deserialize(message));
    }

    @Test
    public void deserializeThrowsWhenNoRequiredSigners() {
        // given
        final byte[] message = concat(
                SIGNING_DOMAIN,
                new byte[] { 0x01 },        // version
                new byte[] { 0x00 },        // signer count (0)
                "Hello\nworld".getBytes(StandardCharsets.UTF_8)
        );

        // then
        assertThrows(IllegalArgumentException.class, () -> OffchainMessage.deserialize(message));
    }

    @Test
    public void deserializeThrowsWhenContentIsEmpty() {
        // given
        final byte[] message = concat(
                SIGNING_DOMAIN,
                new byte[] { 0x01 },        // version
                new byte[] { 0x02 },        // signer count
                SIGNER_A,
                SIGNER_B
                // no trailing content
        );

        // then
        assertThrows(IllegalArgumentException.class, () -> OffchainMessage.deserialize(message));
    }
    //endregion

    private static byte[] concat(byte[]... parts) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }
}
