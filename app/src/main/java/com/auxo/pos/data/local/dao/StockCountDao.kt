// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.auxo.pos.data.local.entity.StockCountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockCountDao {
    @Query("SELECT * FROM stock_counts WHERE status = 'OPEN' ORDER BY id DESC LIMIT 1")
    fun observeOpen(): Flow<StockCountEntity?>

    @Query("SELECT * FROM stock_counts WHERE status = 'OPEN' ORDER BY id DESC LIMIT 1")
    suspend fun getOpen(): StockCountEntity?

    @Query("SELECT * FROM stock_counts WHERE id = :id")
    suspend fun get(id: Long): StockCountEntity?

    @Insert
    suspend fun insert(count: StockCountEntity): Long

    @Query("UPDATE stock_counts SET payload = :payload WHERE id = :id AND status = 'OPEN'")
    suspend fun updatePayload(id: Long, payload: String): Int

    @Query("UPDATE stock_counts SET status = 'CANCELLED', finished_at = :now WHERE id = :id AND status = 'OPEN'")
    suspend fun cancel(id: Long, now: Long): Int

    @Query(
        "UPDATE stock_counts SET status = 'POSTED', finished_at = :now, reason = :reason, note = :note, " +
            "adjusted_count = :adjusted WHERE id = :id AND status = 'OPEN'",
    )
    suspend fun markPosted(id: Long, now: Long, reason: String, note: String?, adjusted: Int): Int
}
