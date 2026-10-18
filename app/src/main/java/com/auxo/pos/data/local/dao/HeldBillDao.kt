package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.auxo.pos.data.local.entity.HeldBillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeldBillDao {
    @Query("SELECT * FROM held_bills ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<HeldBillEntity>>

    @Query("SELECT * FROM held_bills WHERE id = :id")
    suspend fun get(id: Long): HeldBillEntity?

    @Insert
    suspend fun insert(bill: HeldBillEntity): Long

    @Update
    suspend fun update(bill: HeldBillEntity)

    @Query("DELETE FROM held_bills WHERE id = :id")
    suspend fun delete(id: Long)
}
