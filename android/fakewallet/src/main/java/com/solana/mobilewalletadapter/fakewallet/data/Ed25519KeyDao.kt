/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface Ed25519KeyDao {
    @Query("SELECT * FROM keys WHERE public_key_b58 = :publicKeyBase58")
    fun get(publicKeyBase58: String): Ed25519KeyPair?

    @Insert
    fun insert(keypair: Ed25519KeyPair): Long
}