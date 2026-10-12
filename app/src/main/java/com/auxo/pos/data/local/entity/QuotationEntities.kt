package com.auxo.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A quotation. It holds the cart as text (see HeldCartCodec) and nothing else: no stock is touched and no
 * account is changed, whatever happens to it. Added in version 12.
 */
@Entity(tableName = "quotations", indices = [Index(value = ["number"], unique = true)])
data class QuotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** The last valid day, as an epoch day. */
    @ColumnInfo(name = "valid_until_day") val validUntilDay: Long,
    @ColumnInfo(name = "customer_name") val customerName: String,
    @ColumnInfo(name = "customer_phone") val customerPhone: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    val payload: String,
    /** OPEN or CONVERTED. */
    val status: String,
    @ColumnInfo(name = "converted_invoice_id") val convertedInvoiceId: Long? = null,
)
