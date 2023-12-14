/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.walletlib.scenario.AuthorizedAccount;

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
    public final AccountRecord accountRecord;

    @NonNull
    public final String chain;

    @Deprecated
    @NonNull
    public final String cluster;

    @NonNull
    public final byte[] scope;

    @Nullable
    public final Uri walletUriBase;

    @IntRange(from = 1)
    /*package*/ final int accountId;

    @IntRange(from = 1)
    /*package*/ final int walletUriBaseId;

    private boolean mRevoked;

    /*package*/ AuthRecord(@IntRange(from = 1) int id,
                           @NonNull IdentityRecord identity,
                           @NonNull AccountRecord accountRecord,
                           @NonNull String chain,
                           @NonNull byte[] scope,
                           @Nullable Uri walletUriBase,
                           @IntRange(from = 1) int accountId,
                           @IntRange(from = 1) int walletUriBaseId,
                           @IntRange(from = 0) long issued,
                           @IntRange(from = 0) long expires) {
        // N.B. This is a package-visibility constructor; these values will all be validated by
        // other components within this package.
        this.id = id;
        this.identity = identity;
        this.accountRecord = accountRecord;
        this.chain = chain;
        this.cluster = chain;
        this.scope = scope;
        this.walletUriBase = walletUriBase;
        this.accountId = accountId;
        this.walletUriBaseId = walletUriBaseId;
        this.issued = issued;
        this.expires = expires;

        this.publicKey = accountRecord.publicKeyRaw;
        this.accountLabel = accountRecord.accountLabel;
    }

    public AuthorizedAccount authorizedAccount() {
        return new AuthorizedAccount(accountRecord.publicKeyRaw, accountRecord.accountLabel,
                accountRecord.icon, accountRecord.chains, accountRecord.features);
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
                ", chain='" + chain + '\'' +
                ", scope=" + Arrays.toString(scope) +
                ", walletUriBase='" + walletUriBase + '\'' +
                ", issued=" + issued +
                ", expires=" + expires +
                ", mRevoked=" + mRevoked +
                '}';
    }
}
