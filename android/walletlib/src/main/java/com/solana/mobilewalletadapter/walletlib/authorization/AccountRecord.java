package com.solana.mobilewalletadapter.walletlib.authorization;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

/* package */ class AccountRecord {
    @IntRange(from = 1)
    final int id;

    @IntRange(from = 1)
    final int parentId;

    @NonNull
    final byte[] publicKeyRaw;

    @Nullable
    final String accountLabel;

    @Nullable
    final Uri icon;

    @Nullable
    final String[] chains;

    @Nullable
    final String[] features;

    AccountRecord(@IntRange(from = 1) int id,
                  @IntRange(from = 1) int parentId,
                  @NonNull byte[] publicKeyRaw,
                  @Nullable String accountLabel,
                  @Nullable Uri icon,
                  @Nullable String[] chains,
                  @Nullable String[] features) {
        this.id = id;
        this.parentId = parentId;
        this.publicKeyRaw = publicKeyRaw;
        this.accountLabel = accountLabel;
        this.icon = icon;
        this.chains = chains;
        this.features = features;
    }

    @NonNull
    @Override
    public String toString() {
        return "AccountRecord{" +
                "label=" + accountLabel +
                ", publicKey=" + Arrays.toString(publicKeyRaw) +
                '}';
    }
}
