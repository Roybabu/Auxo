package com.auxo.pos.data.local.entity

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
    /** DINE_IN or TAKEAWAY when the bill is an open restaurant order. Added in version 11. */
    @ColumnInfo(name = "service_type") val serviceType: String? = null,
    /** The table of an open dine-in order, normalised (upper case). */
    @ColumnInfo(name = "table_number") val tableNumber: String? = null,
    /** What the kitchen has been told so far, as text (see KitchenOrderCodec). */
    @ColumnInfo(name = "kot_sent") val kotSent: String? = null,
    /** How many kitchen tickets this order has had. */
    @ColumnInfo(name = "kot_count") val kotCount: Int = 0,
)
