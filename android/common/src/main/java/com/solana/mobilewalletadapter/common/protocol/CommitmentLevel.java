/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.common.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum CommitmentLevel {
    Processed("processed"), Confirmed("confirmed"), Finalized("finalized");

    @NonNull
    public final String commitmentLevel;

    CommitmentLevel(@NonNull String commitmentLevel) {
        this.commitmentLevel = commitmentLevel;
    }

    @Nullable
    public static CommitmentLevel fromCommitmentLevelString(@NonNull String commitmentLevel) {
        for (CommitmentLevel cl : values()) {
            if (commitmentLevel.equals(cl.commitmentLevel)) {
                return cl;
            }
        }
        return null;
    }
}
