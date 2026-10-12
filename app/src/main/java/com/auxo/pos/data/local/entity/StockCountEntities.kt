package com.auxo.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stock count. While it is OPEN it only holds what was counted (see StockCounts.encode); no stock changes
 * until it is posted. Added in version 14.
 */
@Entity(tableName = "stock_counts")
data class StockCountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** OPEN, POSTED or CANCELLED. */
    val status: String,
    /** The counted lines as text. */
    val payload: String,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    /** CountReason name, set when posted. */
    val reason: String? = null,
    val note: String? = null,
    /** Items whose stock was adjusted when posted. */
    @ColumnInfo(name = "adjusted_count") val adjustedCount: Int = 0,
)
