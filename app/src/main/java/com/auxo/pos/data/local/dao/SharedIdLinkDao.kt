// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auxo.pos.data.local.entity.SharedIdLinkEntity

@Dao
interface SharedIdLinkDao {
    /** Links are never overwritten: a second link for the same record or shared ID is refused. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(links: List<SharedIdLinkEntity>)

    @Query("SELECT shared_id FROM shared_id_links WHERE table_name = :table AND local_id = :localId")
    suspend fun sharedIdOf(table: String, localId: Long): String?

    @Query("SELECT local_id FROM shared_id_links WHERE table_name = :table AND shared_id = :sharedId")
    suspend fun localIdOf(table: String, sharedId: String): Long?

    @Query("SELECT COUNT(*) FROM shared_id_links")
    suspend fun count(): Int
}
