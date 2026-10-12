package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.auxo.pos.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

/** Add-only: there is deliberately no update or delete here. */
@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>
}
