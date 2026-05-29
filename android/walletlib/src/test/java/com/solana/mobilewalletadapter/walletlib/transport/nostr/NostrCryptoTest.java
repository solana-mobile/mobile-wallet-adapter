/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.transport.nostr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class NostrCryptoTest {

    @Test
    public void testGeneratePrivateKeyReturns32Bytes() {
        // when
        byte[] key = NostrCrypto.generatePrivateKey();

        // then
        assertEquals(32, key.length);
    }

    @Test
    public void testGeneratePrivateKeyReturnsDistinctKeys() {
        // when
        byte[] a = NostrCrypto.generatePrivateKey();
        byte[] b = NostrCrypto.generatePrivateKey();

        // then
        assertFalse(Arrays.equals(a, b));
    }

    @Test
    public void testGetXOnlyPublicKeyReturns32Bytes() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();

        // when
        byte[] pubKey = NostrCrypto.getXOnlyPublicKey(privKey);

        // then
        assertEquals(32, pubKey.length);
    }

    @Test
    public void testGetXOnlyPublicKeyIsDeterministic() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();

        // when
        byte[] pub1 = NostrCrypto.getXOnlyPublicKey(privKey);
        byte[] pub2 = NostrCrypto.getXOnlyPublicKey(privKey);

        // then
        assertArrayEquals(pub1, pub2);
    }

    @Test
    public void testBytesToHexAndHexToBytesRoundTrip() {
        // given
        byte[] original = NostrCrypto.generatePrivateKey();

        // when
        String hex = NostrCrypto.bytesToHex(original);
        byte[] recovered = NostrCrypto.hexToBytes(hex);

        // then
        assertEquals(64, hex.length());
        assertTrue(hex.matches("[0-9a-f]+"));
        assertArrayEquals(original, recovered);
    }

    @Test
    public void testDeriveSessionIdentifierReturns64CharHex() {
        // given
        byte[] pubKey = NostrCrypto.getXOnlyPublicKey(NostrCrypto.generatePrivateKey());

        // when
        String sessionId = NostrCrypto.deriveSessionIdentifier(pubKey);

        // then
        assertEquals(64, sessionId.length());
        assertTrue(sessionId.matches("[0-9a-f]+"));
    }

    @Test
    public void testDeriveSessionIdentifierIsDeterministic() {
        // given
        byte[] pubKey = NostrCrypto.getXOnlyPublicKey(NostrCrypto.generatePrivateKey());

        // when
        String a = NostrCrypto.deriveSessionIdentifier(pubKey);
        String b = NostrCrypto.deriveSessionIdentifier(pubKey);

        // then
        assertEquals(a, b);
    }

    @Test
    public void testSchnorrSignReturns64ByteSignature() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        byte[] message = new byte[32];

        // when
        byte[] sig = NostrCrypto.schnorrSign(message, privKey);

        // then
        assertEquals(64, sig.length);
    }

    @Test
    public void testSchnorrVerifyAcceptsValidSignature() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        byte[] pubKey = NostrCrypto.getXOnlyPublicKey(privKey);
        byte[] message = new byte[32];
        byte[] sig = NostrCrypto.schnorrSign(message, privKey);

        // when
        boolean result = NostrCrypto.schnorrVerify(message, sig, pubKey);

        // then
        assertTrue(result);
    }

    @Test
    public void testSchnorrVerifyRejectsTamperedMessage() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        byte[] pubKey = NostrCrypto.getXOnlyPublicKey(privKey);
        byte[] message = new byte[32];
        byte[] sig = NostrCrypto.schnorrSign(message, privKey);
        message[0] = 1;

        // when
        boolean result = NostrCrypto.schnorrVerify(message, sig, pubKey);

        // then
        assertFalse(result);
    }

    @Test
    public void testSchnorrVerifyRejectsTamperedSignature() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        byte[] pubKey = NostrCrypto.getXOnlyPublicKey(privKey);
        byte[] message = new byte[32];
        byte[] sig = NostrCrypto.schnorrSign(message, privKey);
        sig[0] ^= 0xFF;

        // when
        boolean result = NostrCrypto.schnorrVerify(message, sig, pubKey);

        // then
        assertFalse(result);
    }

    @Test
    public void testSchnorrVerifyRejectsWrongPublicKey() {
        // given
        byte[] privKey1 = NostrCrypto.generatePrivateKey();
        byte[] privKey2 = NostrCrypto.generatePrivateKey();
        byte[] pubKey2 = NostrCrypto.getXOnlyPublicKey(privKey2);
        byte[] message = new byte[32];
        byte[] sig = NostrCrypto.schnorrSign(message, privKey1);

        // when
        boolean result = NostrCrypto.schnorrVerify(message, sig, pubKey2);

        // then
        assertFalse(result);
    }

    @Test
    public void testSchnorrVerifyRejectsInvalidLengthSignature() {
        // given
        byte[] pubKey = NostrCrypto.getXOnlyPublicKey(NostrCrypto.generatePrivateKey());
        byte[] message = new byte[32];

        // when
        boolean tooShort = NostrCrypto.schnorrVerify(message, new byte[63], pubKey);
        boolean tooLong = NostrCrypto.schnorrVerify(message, new byte[65], pubKey);

        // then
        assertFalse(tooShort);
        assertFalse(tooLong);
    }

    @Test
    public void testSchnorrVerifyRejectsInvalidLengthPublicKey() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        byte[] message = new byte[32];
        byte[] sig = NostrCrypto.schnorrSign(message, privKey);

        // when
        boolean tooShort = NostrCrypto.schnorrVerify(message, sig, new byte[31]);
        boolean tooLong = NostrCrypto.schnorrVerify(message, sig, new byte[33]);

        // then
        assertFalse(tooShort);
        assertFalse(tooLong);
    }

    @Test
    public void testBuildEventProducesValidEvent() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        String[][] tags = new String[][]{{"d", "session123"}};

        // when
        JSONObject event = NostrCrypto.buildEvent(privKey, NostrCrypto.NOSTR_EVENT_KIND_MWA,
                "hello", tags);

        // then
        assertTrue(NostrCrypto.verifyEvent(event));
    }

    @Test
    public void testBuildEventHasCorrectFields() throws JSONException {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        String expectedPubkey = NostrCrypto.bytesToHex(NostrCrypto.getXOnlyPublicKey(privKey));
        String[][] tags = new String[][]{{"d", "abc"}, {"p", "def"}};

        // when
        JSONObject event = NostrCrypto.buildEvent(privKey, NostrCrypto.NOSTR_EVENT_KIND_MWA,
                "content", tags);

        // then
        assertEquals(expectedPubkey, event.getString("pubkey"));
        assertEquals(NostrCrypto.NOSTR_EVENT_KIND_MWA, event.getInt("kind"));
        assertEquals("content", event.getString("content"));
        assertEquals(64, event.getString("id").length());
        assertEquals(128, event.getString("sig").length());
        assertTrue(event.getLong("created_at") > 0);
    }

    @Test
    public void testBuildEventWithEmptyContent() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();

        // when
        JSONObject event = NostrCrypto.buildEvent(privKey, NostrCrypto.NOSTR_EVENT_KIND_MWA,
                "", new String[0][]);

        // then
        assertTrue(NostrCrypto.verifyEvent(event));
    }

    @Test
    public void testBuildEventWithSpecialCharacters() {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        String content = "line1\nline2\t\"quoted\"\\backslash";

        // when
        JSONObject event = NostrCrypto.buildEvent(privKey, NostrCrypto.NOSTR_EVENT_KIND_MWA,
                content, new String[0][]);

        // then
        assertTrue(NostrCrypto.verifyEvent(event));
    }

    @Test
    public void testVerifyEventRejectsTamperedId() throws JSONException {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        JSONObject event = NostrCrypto.buildEvent(privKey, NostrCrypto.NOSTR_EVENT_KIND_MWA,
                "test", new String[0][]);
        event.put("id", "00".repeat(32));

        // when
        boolean result = NostrCrypto.verifyEvent(event);

        // then
        assertFalse(result);
    }

    @Test
    public void testVerifyEventRejectsTamperedContent() throws JSONException {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        JSONObject event = NostrCrypto.buildEvent(privKey, NostrCrypto.NOSTR_EVENT_KIND_MWA,
                "original", new String[0][]);
        event.put("content", "modified");

        // when
        boolean result = NostrCrypto.verifyEvent(event);

        // then
        assertFalse(result);
    }

    @Test
    public void testVerifyEventRejectsTamperedSig() throws JSONException {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        JSONObject event = NostrCrypto.buildEvent(privKey, NostrCrypto.NOSTR_EVENT_KIND_MWA,
                "test", new String[0][]);
        event.put("sig", "00".repeat(64));

        // when
        boolean result = NostrCrypto.verifyEvent(event);

        // then
        assertFalse(result);
    }

    @Test
    public void testVerifyEventRejectsTamperedPubkey() throws JSONException {
        // given
        byte[] privKey = NostrCrypto.generatePrivateKey();
        JSONObject event = NostrCrypto.buildEvent(privKey, NostrCrypto.NOSTR_EVENT_KIND_MWA,
                "test", new String[0][]);
        byte[] otherPub = NostrCrypto.getXOnlyPublicKey(NostrCrypto.generatePrivateKey());
        event.put("pubkey", NostrCrypto.bytesToHex(otherPub));

        // when
        boolean result = NostrCrypto.verifyEvent(event);

        // then
        assertFalse(result);
    }

    @Test
    public void testVerifyEventRejectsMissingFields() throws JSONException {
        // given
        JSONObject incomplete = new JSONObject();
        incomplete.put("id", "00".repeat(32));

        // when
        boolean result = NostrCrypto.verifyEvent(incomplete);

        // then
        assertFalse(result);
    }

    @Test
    public void testGetEventTagsSingleTagWithOneValue() throws JSONException {
        // given
        JSONObject event = new JSONObject();
        event.put("tags", new JSONArray("[[\"p\",\"abc123\"]]"));

        // when
        Map<String, String[]> tags = NostrCrypto.getEventTags(event);

        // then
        assertEquals(1, tags.size());
        assertTrue(tags.containsKey("p"));
        assertArrayEquals(new String[]{"abc123"}, tags.get("p"));
    }

    @Test
    public void testGetEventTagsMultipleTags() throws JSONException {
        // given
        JSONObject event = new JSONObject();
        event.put("tags", new JSONArray("[" +
                "[\"p\",\"pubkey1\"]," +
                "[\"d\",\"session123\"]," +
                "[\"msg\",\"MESSAGE\"]" +
                "]"));

        // when
        Map<String, String[]> tags = NostrCrypto.getEventTags(event);

        // then
        assertEquals(3, tags.size());
        assertArrayEquals(new String[]{"pubkey1"}, tags.get("p"));
        assertArrayEquals(new String[]{"session123"}, tags.get("d"));
        assertArrayEquals(new String[]{"MESSAGE"}, tags.get("msg"));
    }

    @Test
    public void testGetEventTagsMultipleValues() throws JSONException {
        // given
        JSONObject event = new JSONObject();
        event.put("tags", new JSONArray("[[\"p\",\"val1\",\"val2\",\"val3\"]]"));

        // when
        Map<String, String[]> tags = NostrCrypto.getEventTags(event);

        // then
        assertEquals(1, tags.size());
        assertArrayEquals(new String[]{"val1", "val2", "val3"}, tags.get("p"));
    }

    @Test
    public void testGetEventTagsEmptyTags() throws JSONException {
        // given
        JSONObject event = new JSONObject();
        event.put("tags", new JSONArray("[]"));

        // when
        Map<String, String[]> tags = NostrCrypto.getEventTags(event);

        // then
        assertEquals(0, tags.size());
    }

    @Test(expected = JSONException.class)
    public void testGetEventTagsMissingTagsField() throws JSONException {
        // given
        JSONObject event = new JSONObject();

        // when
        NostrCrypto.getEventTags(event);
    }

    @Test
    public void testComputeEventIdIsDeterministic() {
        // given
        String pubkey = "00".repeat(32);
        long createdAt = 1700000000L;
        String[][] tags = new String[][]{{"d", "test"}};
        String content = "hello";

        // when
        String id1 = NostrCrypto.computeEventId(pubkey, createdAt,
                NostrCrypto.NOSTR_EVENT_KIND_MWA, tags, content);
        String id2 = NostrCrypto.computeEventId(pubkey, createdAt,
                NostrCrypto.NOSTR_EVENT_KIND_MWA, tags, content);

        // then
        assertEquals(id1, id2);
        assertEquals(64, id1.length());
        assertTrue(id1.matches("[0-9a-f]+"));
    }
}
