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
    private final int id;

    @NonNull
    private final String name;

    @NonNull
    private final Uri uri;

    @NonNull
    private final Uri relativeIconUri;

    @NonNull
    private final byte[] secretKeyCiphertext;

    @NonNull
    private final byte[] secretKeyIV;

    @IntRange(from = 1)
        /*package*/ int getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public Uri getUri() {
        return uri;
    }

    @NonNull
    public Uri getRelativeIconUri() {
        return relativeIconUri;
    }

    @NonNull
    /*package*/  byte[] getSecretKeyCiphertext() {
        return secretKeyCiphertext;
    }

    @NonNull
    /*package*/  byte[] getSecretKeyIV() {
        return secretKeyIV;
    }


    /*package*/ IdentityRecord(IdentityRecordBuilder builder) {
        // N.B. This is a package-visibility constructor; these values will all be validated by
        // other components within this package.
        if (builder.id <= 0) {
            throw new IllegalArgumentException("id must be > 0");
        }
        if (builder.name == null || builder.uri == null || builder.relativeIconUri == null
                || builder.secretKeyCiphertext == null || builder.secretKeyIV == null) {
            throw new IllegalArgumentException("Missing argument");
        }
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

    /*package*/ static class IdentityRecordBuilder {
        private int id;

        private String name;

        private Uri uri;

        private Uri relativeIconUri;

        private byte[] secretKeyCiphertext;

        private byte[] secretKeyIV;

        /*package*/ IdentityRecordBuilder() {
        }

        public IdentityRecordBuilder setId(@IntRange(from = 1) int id) {
            this.id = id;
            return this;
        }

        public IdentityRecordBuilder setName(@NonNull String name) {
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
