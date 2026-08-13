/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SeedDao {
    @Query("SELECT * FROM seed WHERE id = ${Seed.SINGLETON_ID}")
    fun observe(): Flow<Seed?>

    @Query("SELECT * FROM seed WHERE id = ${Seed.SINGLETON_ID}")
    fun get(): Seed?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(seed: Seed)

    @Query("DELETE FROM seed")
    fun delete()
}
