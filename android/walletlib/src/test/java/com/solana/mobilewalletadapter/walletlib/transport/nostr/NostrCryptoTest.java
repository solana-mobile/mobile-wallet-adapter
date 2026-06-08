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

    // ---- BIP-340 official test vectors (https://github.com/bitcoin/bips/blob/master/bip-0340/test-vectors.csv) ----

    @Test
    public void testBip340SignVector0() {
        // given
        byte[] privKey = NostrCrypto.hexToBytes(
                "0000000000000000000000000000000000000000000000000000000000000003");
        byte[] msg = NostrCrypto.hexToBytes(
                "0000000000000000000000000000000000000000000000000000000000000000");
        String expectedSig =
                "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca8215" +
                "25f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0";

        // when
        byte[] sig = NostrCrypto.schnorrSign(msg, privKey);

        // then
        assertEquals(expectedSig, NostrCrypto.bytesToHex(sig));
    }

    @Test
    public void testBip340VerifyVector0() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9");
        byte[] msg = NostrCrypto.hexToBytes(
                "0000000000000000000000000000000000000000000000000000000000000000");
        byte[] sig = NostrCrypto.hexToBytes(
                "E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA8215" +
                "25F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertTrue(result);
    }

    @Test
    public void testBip340VerifyVector1() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "6896BD60EEAE296DB48A229FF71DFE071BDE413E6D43F917DC8DCF8C78DE3341" +
                "8906D11AC976ABCCB20B091292BFF4EA897EFCB639EA871CFA95F6DE339E4B0A");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertTrue(result);
    }

    @Test
    public void testBip340VerifyVector2() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DD308AFEC5777E13121FA72B9CC1B7CC0139715309B086C960E18FD969774EB8");
        byte[] msg = NostrCrypto.hexToBytes(
                "7E2D58D8B3BCDF1ABADEC7829054F90DDA9805AAB56C77333024B9D0A508B75C");
        byte[] sig = NostrCrypto.hexToBytes(
                "5831AAEED7B44BB74E5EAB94BA9D4294C49BCF2A60728D8B4C200F50DD313C1B" +
                "AB745879A5AD954A72C45A91C3A51D3C7ADEA98D82F8481E0E1E03674A6F3FB7");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertTrue(result);
    }

    @Test
    public void testBip340VerifyVector3() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "25D1DFF95105F5253C4022F628A996AD3A0D95FBF21D468A1B33F8C160D8F517");
        byte[] msg = NostrCrypto.hexToBytes(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        byte[] sig = NostrCrypto.hexToBytes(
                "7EB0509757E246F19449885651611CB965ECC1A187DD51B64FDA1EDC9637D5EC" +
                "97582B9CB13DB3933705B32BA982AF5AF25FD78881EBB32771FC5922EFC66EA3");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertTrue(result);
    }

    @Test
    public void testBip340VerifyVector4() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "D69C3509BB99E412E68B0FE8544E72837DFA30746D8BE2AA65975F29D22DC7B9");
        byte[] msg = NostrCrypto.hexToBytes(
                "4DF3C3F68FCC83B27E9D42C90431A72499F17875C81A599B566C9889B9696703");
        byte[] sig = NostrCrypto.hexToBytes(
                "00000000000000000000003B78CE563F89A0ED9414F5AA28AD0D96D6795F9C63" +
                "76AFB1548AF603B3EB45C9F8207DEE1060CB71C04E80F593060B07D28308D7F4");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertTrue(result);
    }

    @Test
    public void testBip340RejectVector5PublicKeyNotOnCurve() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "EEFDEA4CDB677750A420FEE807EACF21EB9898AE79B9768766E4FAA04A2D4A34");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769" +
                "69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    @Test
    public void testBip340RejectVector6HasEvenYRIsFalse() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "FFF97BD5755EEEA420453A14355235D382F6472F8568A18B2F057A146029755" +
                "63CC27944640AC607CD107AE10923D9EF7A73C643E166BE5EBEAFA34B1AC553E2");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    @Test
    public void testBip340RejectVector7NegatedMessage() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "1FA62E331EDBC21C394792D2AB1100A7B432B013DF3F6FF4F99FCB33E0E1515F" +
                "28890B3EDB6E7189B630448B515CE4F8622A954CFE545735AAEA5134FCCDB2BD");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    @Test
    public void testBip340RejectVector8NegatedS() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769" +
                "961764B3AA9B2FFCB6EF947B6887A226E8D7C93E00C5ED0C1834FF0D0C2E6DA6");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    @Test
    public void testBip340RejectVector9SGMinusEPIsInfiniteR0() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "123DDA8328AF9C23A94C1FEECFD123BA4FB73476F0D594DCB65C6425BD186051");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    @Test
    public void testBip340RejectVector10SGMinusEPIsInfiniteR1() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "7615FBAF5AE28864013C099742DEADB4DBA87F11AC6754F93780D5A1837CF197");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    @Test
    public void testBip340RejectVector11RxNotOnCurve() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "4A298DACAE57395A15D0795DDBFD1DCB564DA82B0F269BC70A74F8220429BA1D" +
                "69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    @Test
    public void testBip340RejectVector12RxEqualsFieldSize() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F" +
                "69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    @Test
    public void testBip340RejectVector13SEqualsCurveOrder() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769" +
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    @Test
    public void testBip340RejectVector14PublicKeyExceedsFieldSize() {
        // given
        byte[] pk = NostrCrypto.hexToBytes(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC30");
        byte[] msg = NostrCrypto.hexToBytes(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89");
        byte[] sig = NostrCrypto.hexToBytes(
                "6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769" +
                "69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B");

        // when
        boolean result = NostrCrypto.schnorrVerify(msg, sig, pk);

        // then
        assertFalse(result);
    }

    // ---- Cross-language interop: event signed by @noble/curves (JS), verified by Java ----

    @Test
    public void testVerifyEventSignedByNobleJs() throws JSONException {
        // given
        JSONObject event = new JSONObject();
        event.put("id", "9e81d46d4821572793720dd4a6c1a74b7b531e4fca737b188949eb65eac51b07");
        event.put("pubkey", "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798");
        event.put("created_at", 1700000000L);
        event.put("kind", NostrCrypto.NOSTR_EVENT_KIND_MWA);
        event.put("content", "interop-test");
        event.put("sig",
                "9cf2bd5c9e0453adecd1591113f62bd7ab45adf6c0e020d78a0544ecccf560d6" +
                "b2a480579c56cacfc6f1af3d8545330f54c99a6c76b6be29a63ec82a47c3e595");
        event.put("tags", new JSONArray("[[\"d\",\"session123\"]]"));

        // when
        boolean result = NostrCrypto.verifyEvent(event);

        // then
        assertTrue(result);
    }
}
