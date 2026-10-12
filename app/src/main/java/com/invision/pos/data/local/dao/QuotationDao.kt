package com.invision.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.invision.pos.data.local.entity.QuotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuotationDao {
    @Query("SELECT * FROM quotations ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<QuotationEntity>>

    @Query("SELECT * FROM quotations WHERE id = :id")
    suspend fun get(id: Long): QuotationEntity?

    @Insert
    suspend fun insert(quotation: QuotationEntity): Long

    @Query("DELETE FROM quotations WHERE id = :id")
    suspend fun delete(id: Long)

    /** Marks an open quotation as used. Returns 0 when it was already converted or is gone. */
    @Query("UPDATE quotations SET status = 'CONVERTED', converted_invoice_id = :invoiceId WHERE id = :id AND status = 'OPEN'")
    suspend fun markConverted(id: Long, invoiceId: Long): Int
}
