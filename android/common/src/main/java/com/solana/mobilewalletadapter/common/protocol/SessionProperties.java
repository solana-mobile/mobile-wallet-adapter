/*
 * Copyright (c) 2023 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.common.protocol;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public class SessionProperties {
    private static final String PROTOCOL_VERSION_KEY = "v";
    @NonNull
    public ProtocolVersion protocolVersion;

    public SessionProperties(@NonNull ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public static SessionProperties deserialize(byte[] bytes) throws JSONException {
        JSONObject json = new JSONObject(new String(bytes));
        String protocolVersionString = json.getString(PROTOCOL_VERSION_KEY);
        return new SessionProperties(ProtocolVersion.from(protocolVersionString));
    }

    public byte[] serialize() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(PROTOCOL_VERSION_KEY, protocolVersion);
        return json.toString().getBytes();
    }

    public enum ProtocolVersion {
        LEGACY(0), V1(1);

        final int versionInt;

        ProtocolVersion(int versionInt) {
            this.versionInt = versionInt;
        }

        @NonNull
        @Override
        public String toString() {
            switch (this) {
                case V1:
                    return "1";
                default:
                    return "legacy";
            }
        }

        public static ProtocolVersion from(String versionString) throws IllegalArgumentException {
            switch (versionString.toLowerCase()) {
                case "v1":
                case "1":
                    return V1;
                case "legacy":
                    return LEGACY;
                default:
                    throw new IllegalArgumentException("Unknown/unsupported version: " + versionString);
            }
        }
    }
}
