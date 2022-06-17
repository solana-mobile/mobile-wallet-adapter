/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Ed25519KeyPair::class], version = 1, exportSchema = false)
internal abstract class Ed25519KeyDatabase : RoomDatabase() {
    abstract fun keysDao(): Ed25519KeyDao
}