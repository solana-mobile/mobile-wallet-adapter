/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.transport.nostr;

import androidx.annotation.NonNull;

import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NostrCrypto {
    public static final int NOSTR_EVENT_KIND_MWA = 20012;

    private static final ECNamedCurveParameterSpec SECP256K1_SPEC =
            ECNamedCurveTable.getParameterSpec("secp256k1");
    private static final ECDomainParameters SECP256K1_DOMAIN = new ECDomainParameters(
            SECP256K1_SPEC.getCurve(), SECP256K1_SPEC.getG(),
            SECP256K1_SPEC.getN(), SECP256K1_SPEC.getH());

    @NonNull
    public static byte[] generatePrivateKey() {
        byte[] privKey = new byte[32];
        new SecureRandom().nextBytes(privKey);
        BigInteger k = new BigInteger(1, privKey);
        if (k.compareTo(BigInteger.ONE) < 0 || k.compareTo(SECP256K1_SPEC.getN()) >= 0) {
            return generatePrivateKey();
        }
        return privKey;
    }

    @NonNull
    public static byte[] getXOnlyPublicKey(@NonNull byte[] privateKey) {
        BigInteger k = new BigInteger(1, privateKey);
        ECPoint point = SECP256K1_SPEC.getG().multiply(k).normalize();
        byte[] x = point.getAffineXCoord().getEncoded();
        if (x.length == 32) return x;
        byte[] result = new byte[32];
        System.arraycopy(x, x.length - 32, result, 0, 32);
        return result;
    }

    @NonNull
    public static String bytesToHex(@NonNull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    @NonNull
    public static byte[] hexToBytes(@NonNull String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    @NonNull
    public static String deriveSessionIdentifier(@NonNull byte[] associationPublicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(associationPublicKey);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @NonNull
    public static String computeEventId(@NonNull String pubkey, long createdAt, int kind,
                                         @NonNull String[][] tags, @NonNull String content) {
        String serialized = serializeEvent(pubkey, createdAt, kind, tags, content);
        return bytesToHex(sha256(serialized.getBytes(StandardCharsets.UTF_8)));
    }

    @NonNull
    public static byte[] schnorrSign(@NonNull byte[] messageHash, @NonNull byte[] privateKey) {
        // hardcoded zero randomness here - this produces valid signatures which is all we need for interacting with
        // Nostr. MWA has its own payload encryption, the Nostr keypair is only used for routing and message verification.
        BigInteger k0 = taggedHash("BIP0340/aux", new byte[32]);
        BigInteger d = new BigInteger(1, privateKey);
        ECPoint P = SECP256K1_SPEC.getG().multiply(d).normalize();

        if (P.getAffineYCoord().toBigInteger().testBit(0)) {
            d = SECP256K1_SPEC.getN().subtract(d);
        }

        byte[] dBytes = bigIntTo32Bytes(d);
        byte[] t = xor32(dBytes, bigIntTo32Bytes(k0));

        byte[] rand = taggedHashBytes("BIP0340/nonce",
                concat(t, P.getAffineXCoord().getEncoded(), messageHash));
        BigInteger kPrime = new BigInteger(1, rand).mod(SECP256K1_SPEC.getN());
        if (kPrime.equals(BigInteger.ZERO)) {
            throw new RuntimeException("Schnorr signing failed: k' is zero");
        }

        ECPoint R = SECP256K1_SPEC.getG().multiply(kPrime).normalize();
        BigInteger k = R.getAffineYCoord().toBigInteger().testBit(0)
                ? SECP256K1_SPEC.getN().subtract(kPrime) : kPrime;

        byte[] rBytes = R.getAffineXCoord().getEncoded();
        byte[] eHash = taggedHashBytes("BIP0340/challenge",
                concat(rBytes, P.getAffineXCoord().getEncoded(), messageHash));
        BigInteger e = new BigInteger(1, eHash).mod(SECP256K1_SPEC.getN());

        BigInteger s = k.add(e.multiply(d)).mod(SECP256K1_SPEC.getN());

        byte[] sig = new byte[64];
        byte[] rEnc = bigIntTo32Bytes(new BigInteger(1, rBytes));
        byte[] sEnc = bigIntTo32Bytes(s);
        System.arraycopy(rEnc, 0, sig, 0, 32);
        System.arraycopy(sEnc, 0, sig, 32, 32);
        return sig;
    }

    public static boolean schnorrVerify(@NonNull byte[] messageHash, @NonNull byte[] signature,
                                         @NonNull byte[] publicKey) {
        if (signature.length != 64 || publicKey.length != 32) return false;

        try {
            BigInteger px = new BigInteger(1, publicKey);
            if (px.compareTo(SECP256K1_SPEC.getCurve().getField().getCharacteristic()) >= 0) {
                return false;
            }

            ECPoint P = liftX(px);
            if (P == null) return false;

            byte[] rBytes = new byte[32];
            byte[] sBytes = new byte[32];
            System.arraycopy(signature, 0, rBytes, 0, 32);
            System.arraycopy(signature, 32, sBytes, 0, 32);

            BigInteger r = new BigInteger(1, rBytes);
            BigInteger s = new BigInteger(1, sBytes);
            if (r.compareTo(SECP256K1_SPEC.getCurve().getField().getCharacteristic()) >= 0
                    || s.compareTo(SECP256K1_SPEC.getN()) >= 0) {
                return false;
            }

            byte[] eHash = taggedHashBytes("BIP0340/challenge",
                    concat(rBytes, publicKey, messageHash));
            BigInteger e = new BigInteger(1, eHash).mod(SECP256K1_SPEC.getN());

            ECPoint R = SECP256K1_SPEC.getG().multiply(s)
                    .add(P.multiply(SECP256K1_SPEC.getN().subtract(e))).normalize();

            if (R.isInfinity()) return false;
            if (R.getAffineYCoord().toBigInteger().testBit(0)) return false;
            return R.getAffineXCoord().toBigInteger().equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verifyEvent(@NonNull JSONObject event) {
        try {
            String id = event.getString("id");
            String pubkey = event.getString("pubkey");
            String sig = event.getString("sig");
            long createdAt = event.getLong("created_at");
            int kind = event.getInt("kind");
            String content = event.getString("content");

            JSONArray tagsArray = event.getJSONArray("tags");
            String[][] tags = new String[tagsArray.length()][];
            for (int i = 0; i < tagsArray.length(); i++) {
                JSONArray tag = tagsArray.getJSONArray(i);
                tags[i] = new String[tag.length()];
                for (int j = 0; j < tag.length(); j++) {
                    tags[i][j] = tag.getString(j);
                }
            }

            String expectedId = computeEventId(pubkey, createdAt, kind, tags, content);
            if (!expectedId.equals(id)) return false;

            return schnorrVerify(hexToBytes(id), hexToBytes(sig), hexToBytes(pubkey));
        } catch (JSONException e) {
            return false;
        }
    }

    public static Map<String, String[]> getEventTags(@NonNull JSONObject event) throws JSONException {
        JSONArray tagsArray = event.getJSONArray("tags");
        Map<String, String[]> tags = new HashMap<>();
        for (int i = 0; i < tagsArray.length(); i++) {
            JSONArray tag = tagsArray.getJSONArray(i);
            String tagKey = tag.getString(0);
            String[] tagValues = new String[tag.length()-1];
            for (int j = 0; j < tagValues.length; j++) {
                tagValues[j] = tag.getString(j+1);
            }
            tags.put(tagKey, tagValues);
        }

        return tags;
    }

    @NonNull
    public static JSONObject buildEvent(@NonNull byte[] privateKey, int kind,
                                         @NonNull String content, @NonNull String[][] tags) {
        String pubkey = bytesToHex(getXOnlyPublicKey(privateKey));
        long createdAt = System.currentTimeMillis() / 1000;
        String id = computeEventId(pubkey, createdAt, kind, tags, content);
        byte[] sig = schnorrSign(hexToBytes(id), privateKey);

        try {
            JSONObject event = new JSONObject();
            event.put("id", id);
            event.put("pubkey", pubkey);
            event.put("created_at", createdAt);
            event.put("kind", kind);
            event.put("content", content);
            event.put("sig", bytesToHex(sig));

            JSONArray tagsJson = new JSONArray();
            for (String[] tag : tags) {
                JSONArray tagJson = new JSONArray();
                for (String val : tag) {
                    tagJson.put(val);
                }
                tagsJson.put(tagJson);
            }
            event.put("tags", tagsJson);
            return event;
        } catch (JSONException e) {
            throw new RuntimeException("Failed to build Nostr event", e);
        }
    }

    // --- Private helpers ---

    @NonNull
    private static String serializeEvent(@NonNull String pubkey, long createdAt, int kind,
                                          @NonNull Map<String, String[]> tags, @NonNull String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("[0,\"").append(pubkey).append("\",")
                .append(createdAt).append(",").append(kind).append(",");

        sb.append("[");
        int i = 0;
        for (Map.Entry<String, String[]> entry : tags.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append("[\"").append(escapeJsonString(entry.getKey())).append("\"");
            for (String value : entry.getValue()) {
                sb.append(",\"").append(escapeJsonString(value)).append("\"");
            }
            sb.append("]");
            i++;
        }
        sb.append("],");

        sb.append("\"").append(escapeJsonString(content)).append("\"]");
        return sb.toString();
    }

    @NonNull
    private static String serializeEvent(@NonNull String pubkey, long createdAt, int kind,
                                         @NonNull String[][] tags, @NonNull String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("[0,\"").append(pubkey).append("\",")
                .append(createdAt).append(",").append(kind).append(",");

        sb.append("[");
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("[");
            for (int j = 0; j < tags[i].length; j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(escapeJsonString(tags[i][j])).append("\"");
            }
            sb.append("]");
        }
        sb.append("],");

        sb.append("\"").append(escapeJsonString(content)).append("\"]");
        return sb.toString();
    }

    @NonNull
    private static String escapeJsonString(@NonNull String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\n': sb.append("\\n"); break;
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    @NonNull
    private static byte[] sha256(@NonNull byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @NonNull
    private static BigInteger taggedHash(@NonNull String tag, @NonNull byte[] data) {
        byte[] tagHash = sha256(tag.getBytes(StandardCharsets.UTF_8));
        byte[] buf = new byte[tagHash.length * 2 + data.length];
        System.arraycopy(tagHash, 0, buf, 0, tagHash.length);
        System.arraycopy(tagHash, 0, buf, tagHash.length, tagHash.length);
        System.arraycopy(data, 0, buf, tagHash.length * 2, data.length);
        return new BigInteger(1, sha256(buf));
    }

    @NonNull
    private static byte[] taggedHashBytes(@NonNull String tag, @NonNull byte[] data) {
        byte[] tagHash = sha256(tag.getBytes(StandardCharsets.UTF_8));
        byte[] buf = new byte[tagHash.length * 2 + data.length];
        System.arraycopy(tagHash, 0, buf, 0, tagHash.length);
        System.arraycopy(tagHash, 0, buf, tagHash.length, tagHash.length);
        System.arraycopy(data, 0, buf, tagHash.length * 2, data.length);
        return sha256(buf);
    }

    @NonNull
    private static byte[] bigIntTo32Bytes(@NonNull BigInteger val) {
        byte[] bytes = val.toByteArray();
        if (bytes.length == 32) return bytes;
        byte[] result = new byte[32];
        if (bytes.length > 32) {
            System.arraycopy(bytes, bytes.length - 32, result, 0, 32);
        } else {
            System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
        }
        return result;
    }

    @NonNull
    private static byte[] xor32(@NonNull byte[] a, @NonNull byte[] b) {
        byte[] result = new byte[32];
        for (int i = 0; i < 32; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    @NonNull
    private static byte[] concat(@NonNull byte[]... arrays) {
        int totalLen = 0;
        for (byte[] a : arrays) totalLen += a.length;
        byte[] result = new byte[totalLen];
        int offset = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, offset, a.length);
            offset += a.length;
        }
        return result;
    }

    private static ECPoint liftX(@NonNull BigInteger x) {
        BigInteger p = SECP256K1_SPEC.getCurve().getField().getCharacteristic();
        BigInteger ySquared = x.modPow(BigInteger.valueOf(3), p).add(BigInteger.valueOf(7)).mod(p);
        BigInteger y = ySquared.modPow(p.add(BigInteger.ONE).divide(BigInteger.valueOf(4)), p);
        if (!y.modPow(BigInteger.valueOf(2), p).equals(ySquared)) return null;
        if (y.testBit(0)) y = p.subtract(y);
        try {
            return SECP256K1_SPEC.getCurve().createPoint(x, y).normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private NostrCrypto() {}
}
