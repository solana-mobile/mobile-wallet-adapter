/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Objects;

public class AuthRecord {
    @IntRange(from = 1)
    /*package*/ final int id;

    @NonNull
    public final IdentityRecord identity;

    @IntRange(from = 0)
    public final long issued;

    @IntRange(from = 0)
    public final long expires;

    @NonNull
    public final byte[] publicKey;

    @Nullable
    public final String accountLabel;

    @NonNull
    public final String cluster;

    @NonNull
    public final byte[] scope;

    @Nullable
    public final Uri walletUriBase;

    @IntRange(from = 1)
    /*package*/ final int publicKeyId;

    @IntRange(from = 1)
    /*package*/ final int walletUriBaseId;

    private boolean mRevoked;

    /*package*/ AuthRecord(@IntRange(from = 1) int id,
                           @NonNull IdentityRecord identity,
                           @NonNull byte[] publicKey,
                           @Nullable String accountLabel,
                           @NonNull String cluster,
                           @NonNull byte[] scope,
                           @Nullable Uri walletUriBase,
                           @IntRange(from = 1) int publicKeyId,
                           @IntRange(from = 1) int walletUriBaseId,
                           @IntRange(from = 0) long issued,
                           @IntRange(from = 0) long expires) {
        // N.B. This is a package-visibility constructor; these values will all be validated by
        // other components within this package.
        this.id = id;
        this.identity = identity;
        this.publicKey = publicKey;
        this.accountLabel = accountLabel;
        this.cluster = cluster;
        this.scope = scope;
        this.walletUriBase = walletUriBase;
        this.publicKeyId = publicKeyId;
        this.walletUriBaseId = walletUriBaseId;
        this.issued = issued;
        this.expires = expires;
    }

    public boolean isExpired() {
        final long now = System.currentTimeMillis();
        return (now < issued || now > expires);
    }

    /*package*/ void setRevoked() {
        synchronized (this) {
            mRevoked = true;
        }
    }

    public boolean isRevoked() {
        synchronized (this) {
            return mRevoked;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthRecord that = (AuthRecord) o;
        return id == that.id; // equality is strictly defined by the database primary key
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // id is the database primary key
    }

    @NonNull
    @Override
    public String toString() {
        return "AuthRecord{" +
                "id=" + id +
                ", identity=" + identity +
                ", publicKey=" + Arrays.toString(publicKey) +
                ", cluster='" + cluster + '\'' +
                ", scope=" + Arrays.toString(scope) +
                ", walletUriBase='" + walletUriBase + '\'' +
                ", issued=" + issued +
                ", expires=" + expires +
                ", mRevoked=" + mRevoked +
                '}';
    }
}
