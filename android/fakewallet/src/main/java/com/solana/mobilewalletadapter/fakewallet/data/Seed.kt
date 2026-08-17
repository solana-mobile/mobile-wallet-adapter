/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Single-row table (id is always SINGLETON_ID) holding the optional persistent seed phrase.
// The derivation path is fixed (m/44'/501'/account_index'/0'); keys are derived on demand.
@Entity(tableName = "seed")
internal data class Seed(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "mnemonic") val mnemonic: String,
    @ColumnInfo(name = "account_index") val accountIndex: Int = 0
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
