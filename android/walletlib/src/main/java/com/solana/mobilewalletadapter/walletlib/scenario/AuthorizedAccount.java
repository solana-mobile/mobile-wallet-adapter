/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

public class AuthorizedAccount {
    @NonNull
    public final byte[] publicKey;
    @Nullable
    public final String displayAddress;
    @Nullable
    public final String displayAddressFormat;
    @Nullable
    public final String accountLabel;
    @Nullable
    public final Uri accountIcon;
    @Nullable
    public final String[] chains;
    @Nullable
    public final String[] features;

    /**
     * The account icon URI
     * @deprecated
     * Use {@link AuthorizedAccount#accountIcon} instead
     */
    @Deprecated
    @Nullable
    public final Uri icon;

    public AuthorizedAccount(@NonNull byte[] publicKey,
                             @Nullable String accountLabel,
                             @Nullable Uri accountIcon,
                             @Nullable String[] chains,
                             @Nullable String[] features) {
        this.publicKey = publicKey;
        this.displayAddress = null;
        this.displayAddressFormat = null;
        this.accountLabel = accountLabel;
        this.accountIcon = accountIcon;
        this.icon = accountIcon;
        this.chains = chains;
        this.features = features;
    }

    public AuthorizedAccount(@NonNull byte[] publicKey,
                             @Nullable String displayAddress,
                             @Nullable String displayAddressFormat,
                             @Nullable String accountLabel,
                             @Nullable Uri accountIcon,
                             @Nullable String[] chains,
                             @Nullable String[] features) {
        this.publicKey = publicKey;
        this.displayAddress = displayAddress;
        this.displayAddressFormat = displayAddressFormat;
        this.accountLabel = accountLabel;
        this.accountIcon = accountIcon;
        this.icon = accountIcon;
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
