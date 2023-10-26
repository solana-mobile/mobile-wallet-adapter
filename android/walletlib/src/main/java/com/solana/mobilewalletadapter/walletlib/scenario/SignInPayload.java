package com.solana.mobilewalletadapter.walletlib.scenario;


import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SignInPayload {
    @NonNull
    public final String domain;
    @Nullable
    public final String address;
    @Nullable
    public final String statement;
    @NonNull
    public final Uri uri;
    @NonNull
    public final String version;
    public final int chainId;
    @NonNull
    public final String nonce;
    @NonNull
    public final String issuedAt;
    @Nullable
    public final String expirationTime;
    @Nullable
    public final String notBefore;
    @Nullable
    public final String requestId;
    @Nullable
    @Size(min = 1)
    public final Uri[] resources;

    public SignInPayload(@NonNull String domain,
                         @Nullable String statement,
                         @NonNull Uri uri,
                         @Nullable String issuedAt) {
        this(domain, null, statement, uri, "1", 1, null,
                issuedAt, null, null, null, null);
    }

    public SignInPayload(@NonNull String domain,
                         @Nullable String address,
                         @Nullable String statement,
                         @NonNull Uri uri,
                         @NonNull String version,
                         int chainId,
                         @NonNull String nonce,
                         @NonNull String issuedAt,
                         @Nullable String expirationTime,
                         @Nullable String notBefore,
                         @Nullable String requestId,
                         @Nullable Uri[] resources) {
        this.domain = domain;
        this.address = address;
        this.statement = statement;
        this.uri = uri;
        this.version = version;
        this.chainId = chainId;
        this.requestId = requestId;
        this.resources = resources;
        this.nonce = nonce;
        this.issuedAt = issuedAt;
        this.expirationTime = expirationTime;
        this.notBefore = notBefore;
    }

    public static SignInPayload fromJson(JSONObject json) throws JSONException {
        Uri[] resources = null;

        JSONArray resourcesArr = json.optJSONArray("resources");
        if (resourcesArr  != null) {
            resources = new Uri[resourcesArr .length()];
            for (int i = 0; i < resourcesArr .length(); i++) {
                resources[i] = Uri.parse(resourcesArr.getString(i));
            }
        }

        return new SignInPayload(
                json.getString("domain"),
                json.has("address") ? json.getString("address") : null,
                json.has("statement") ? json.getString("statement") : null,
                Uri.parse(json.getString("uri")),
                json.getString("version"),
                json.getInt("chainId"),
                json.getString("nonce"),
                json.getString("issuedAt"),
                json.has("expirationTime") ? json.getString("expirationTime") : null,
                json.has("notBefore") ? json.getString("notBefore") : null,
                json.has("requestId") ? json.getString("requestId") : null,
                resources
        );
    }
}
