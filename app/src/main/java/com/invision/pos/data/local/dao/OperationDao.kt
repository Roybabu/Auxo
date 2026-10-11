package com.invision.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.invision.pos.data.local.entity.AppliedRequestEntity
import com.invision.pos.data.local.entity.NumberSequenceEntity

/** Document numbering and one-time request ids, used inside other transactions. */
@Dao
abstract class OperationDao {

    @Query("SELECT next_value FROM number_sequences WHERE name = :name")
    abstract suspend fun sequenceValue(name: String): Long?

    @Upsert
    abstract suspend fun upsertSequence(sequence: NumberSequenceEntity)

    /** Takes the next number in [sequenceName]. Call inside a transaction so a rollback returns it. */
    open suspend fun nextNumber(sequenceName: String, formatNumber: (Long) -> String): String {
        val value = sequenceValue(sequenceName) ?: 1L
        upsertSequence(NumberSequenceEntity(sequenceName, value + 1))
        return formatNumber(value)
    }

    /** Returns -1 if the request id was already used. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRequest(request: AppliedRequestEntity): Long

    @Query("SELECT * FROM applied_requests WHERE request_id = :requestId")
    abstract suspend fun getRequest(requestId: String): AppliedRequestEntity?

    @Query("UPDATE applied_requests SET result_id = :resultId WHERE request_id = :requestId")
    abstract suspend fun setRequestResult(requestId: String, resultId: Long)
}
