package com.invision.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line of the activity log: who did what to which record, and when. Added in version 4.
 * Lines are only ever added, never changed or removed by the app. The name and role are copied in
 * at the time so the line still reads correctly if the account changes later.
 */
@Entity(
    tableName = "audit_log",
    indices = [Index("created_at"), Index(value = ["entity_type", "entity_id"])],
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "actor_id") val actorId: Long,
    @ColumnInfo(name = "actor_name") val actorName: String,
    @ColumnInfo(name = "actor_role") val actorRole: String,
    val action: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: Long?,
    val summary: String,
)
