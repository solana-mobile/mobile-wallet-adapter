/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common.util;

import android.util.Base64;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;

public class JsonPack {
    @NonNull
    public static JSONArray packByteArraysToBase64PayloadsArray(@NonNull byte[][] byteArrays) {
        final JSONArray arr = new JSONArray();
        for (byte[] byteArray : byteArrays) {
            final String b64 =
                Base64.encodeToString(byteArray,Base64.NO_WRAP | Base64.NO_PADDING);
            arr.put(b64);
        }
        return arr;
    }

    @NonNull
    public static byte[][] unpackBase64PayloadsArrayToByteArrays(@NonNull JSONArray arr)
            throws JSONException {
        final int numEntries = arr.length();
        final byte[][] byteArrays = new byte[numEntries][];
        for (int i = 0; i < numEntries; i++) {
            final String b64 = arr.getString(i);
            byteArrays[i] = Base64.decode(b64, Base64.DEFAULT);
        }
        return byteArrays;
    }

    @NonNull
    public static JSONArray packBooleans(@NonNull boolean[] booleans) {
        final JSONArray arr = new JSONArray();
        for (boolean b : booleans) {
            arr.put(b);
        }
        return arr;
    }

    @NonNull
    public static boolean[] unpackBooleans(@NonNull JSONArray arr)
            throws JSONException {
        final int numEntries = arr.length();
        final boolean[] booleans = new boolean[numEntries];
        for (int i = 0; i < numEntries; i++) {
            booleans[i] = arr.getBoolean(i);
        }
        return booleans;
    }

    @NonNull
    public static JSONArray packStrings(@NonNull String[] strings) {
        final JSONArray arr = new JSONArray();
        for (String s : strings) {
            arr.put(s);
        }
        return arr;
    }

    @NonNull
    public static String[] unpackStrings(@NonNull JSONArray arr)
            throws JSONException {
        final int numEntries = arr.length();
        final String[] strings = new String[numEntries];
        for (int i = 0; i < numEntries; i++) {
            strings[i] = arr.getString(i);
        }
        return strings;
    }

    private JsonPack() {}
}
