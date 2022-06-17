/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Objects;

public class IdentityRecord {
    @IntRange(from = 1)
    /*package*/ final int id;

    @NonNull
    public final String name;

    @NonNull
    public final Uri uri;

    @NonNull
    public final Uri relativeIconUri;

    @NonNull
    /*package*/ final byte[] secretKeyCiphertext;

    @NonNull
    /*package*/ final byte[] secretKeyIV;

    /*package*/ IdentityRecord(int id,
                               @NonNull String name,
                               @NonNull Uri uri,
                               @NonNull Uri relativeIconUri,
                               @NonNull byte[] secretKeyCiphertext,
                               @NonNull byte[] secretKeyIV) {
        // N.B. This is a package-visibility constructor; these values will all be validated by
        // other components within this package.
        this.id = id;
        this.name = name;
        this.uri = uri;
        this.relativeIconUri = relativeIconUri;
        this.secretKeyCiphertext = secretKeyCiphertext;
        this.secretKeyIV = secretKeyIV;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentityRecord that = (IdentityRecord) o;
        return id == that.id; // equality is strictly defined by the database primary key
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // id is the database primary key
    }

    @NonNull
    @Override
    public String toString() {
        return "IdentityRecord{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", uri=" + uri +
                ", relativeIconUri=" + relativeIconUri +
                '}';
    }
}
