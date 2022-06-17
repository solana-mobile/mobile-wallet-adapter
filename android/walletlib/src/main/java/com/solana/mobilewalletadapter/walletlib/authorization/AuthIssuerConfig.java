/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

public class AuthIssuerConfig {
    public static final int DEFAULT_MAX_OUTSTANDING_TOKENS_PER_IDENTITY = 50;
    public static final long DEFAULT_AUTHORIZATION_VALIDITY_MS = 60L * 60L * 1000L; // 1 hour
    public static final long DEFAULT_REAUTHORIZATION_VALIDITY_MS = 30L * 24L * 60L * 60L * 1000L; // 30 days
    public static final long DEFAULT_REAUTHORIZATION_NOP_DURATION_MS = 10L * 60L * 1000L; // 10 minutes

    @NonNull
    public final String name;

    @IntRange(from = 1)
    public final int maxOutstandingTokensPerIdentity;

    @IntRange(from = 1)
    public final long authorizationValidityMs;

    @IntRange(from = 0)
    public final long reauthorizationValidityMs;

    @IntRange(from = 0)
    public final long reauthorizationNopDurationMs;

    public AuthIssuerConfig(@NonNull String name) {
        this(name, DEFAULT_MAX_OUTSTANDING_TOKENS_PER_IDENTITY, DEFAULT_AUTHORIZATION_VALIDITY_MS,
                DEFAULT_REAUTHORIZATION_VALIDITY_MS, DEFAULT_REAUTHORIZATION_NOP_DURATION_MS);
    }

    public AuthIssuerConfig(@NonNull String name,
                            @IntRange(from = 1) int maxOutstandingTokensPerIdentity,
                            @IntRange(from = 1) long authorizationValidityMs,
                            @IntRange(from = 0) long reauthorizationValidityMs,
                            @IntRange(from = 0) long reauthorizationNopDurationMs) {
        this.name = name;
        this.maxOutstandingTokensPerIdentity = maxOutstandingTokensPerIdentity;
        this.authorizationValidityMs = authorizationValidityMs;
        this.reauthorizationValidityMs = reauthorizationValidityMs;
        this.reauthorizationNopDurationMs = reauthorizationNopDurationMs;
    }
}
