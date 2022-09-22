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

    /*package*/ IdentityRecord(IdentityRecordBuilder builder) {
        // N.B. This is a package-visibility constructor; these values will all be validated by
        // other components within this package.
        this.id = builder.id;
        this.name = builder.name;
        this.uri = builder.uri;
        this.relativeIconUri = builder.relativeIconUri;
        this.secretKeyCiphertext = builder.secretKeyCiphertext;
        this.secretKeyIV = builder.secretKeyIV;
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

    public static class IdentityRecordBuilder {
        @IntRange(from = 1)
        private int id;

        @NonNull
        private String name;

        @NonNull
        private Uri uri;

        @NonNull
        private Uri relativeIconUri;

        @NonNull
        private byte[] secretKeyCiphertext;

        @NonNull
        private byte[] secretKeyIV;

        public IdentityRecordBuilder() {
        }

        public IdentityRecordBuilder setId(int id) {
            this.id = id;
            return this;
        }

        public IdentityRecordBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public IdentityRecordBuilder setUri(@NonNull Uri uri) {
            this.uri = uri;
            return this;
        }

        public IdentityRecordBuilder setRelativeIconUri(@NonNull Uri relativeIconUri) {
            this.relativeIconUri = relativeIconUri;
            return this;
        }

        public IdentityRecordBuilder setSecretKeyCiphertext(@NonNull byte[] secretKeyCiphertext) {
            this.secretKeyCiphertext = secretKeyCiphertext;
            return this;
        }

        public IdentityRecordBuilder setSecretKeyIV(@NonNull byte[] secretKeyIV) {
            this.secretKeyIV = secretKeyIV;
            return this;
        }

        public IdentityRecord build() {
            return new IdentityRecord(this);
        }

    }
}
