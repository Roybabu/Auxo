package com.invision.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.invision.pos.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

/** Add-only: there is deliberately no update or delete here. */
@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId ORDER BY created_at DESC, id DESC")
    suspend fun forEntity(entityType: String, entityId: Long): List<AuditLogEntity>
}
