/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

public class AuthorizedAccount {
    @NonNull
    public final byte[] publicKey;
    @Nullable
    public final String accountLabel;
    @Nullable
    public final String[] chains;
    @Nullable
    public final String[] features;

    public AuthorizedAccount(@NonNull byte[] publicKey,
                      @Nullable String accountLabel,
                      @Nullable String[] chains,
                      @Nullable String[] features) {
        this.publicKey = publicKey;
        this.accountLabel = accountLabel;
        this.chains = chains;
        this.features = features;
    }

    @NonNull
    @Override
    public String toString() {
        return "AuthorizedAccount{" +
                "publicKey=" + Arrays.toString(publicKey) +
                ", accountLabel='" + accountLabel + '\'' +
                ", chains=" + Arrays.toString(chains) +
                ", features=" + Arrays.toString(features) +
                '}';
    }
}
