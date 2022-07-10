/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.data

import androidx.annotation.Size
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keys")
internal data class Ed25519KeyPair(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(
        name = "public_key_b64",
        typeAffinity = ColumnInfo.TEXT
    ) val publicKeyBase64: String,
    @ColumnInfo(
        name = "private key",
        typeAffinity = ColumnInfo.BLOB
    ) @Size(PRIVATE_KEY_SIZE.toLong()) val privateKey: ByteArray
) {
    init {
        require(privateKey.size == PRIVATE_KEY_SIZE) { "Invalid private key length" }
    }

    companion object {
        const val PRIVATE_KEY_SIZE = 32
    }
}