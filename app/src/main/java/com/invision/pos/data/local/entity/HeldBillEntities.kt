package com.invision.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A bill parked at the counter. It holds the cart as text (see HeldCartCodec) and nothing else: no stock is
 * reserved and no number is used, so a held bill changes nothing until it is finished. Added in version 10.
 */
@Entity(tableName = "held_bills")
data class HeldBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    val payload: String,
)
