package com.auxo.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auxo.pos.data.local.entity.DayClosingEntity
import com.auxo.pos.data.local.entity.DayCountsRow
import com.auxo.pos.data.local.entity.PaymentTotalRow
import com.auxo.pos.data.local.entity.ReceiptTotalRow
import kotlinx.coroutines.flow.Flow

/** Closing records are added and read, never changed or removed: there is deliberately no update or delete. */
@Dao
interface DayEndDao {

    /** Returns -1 when that day is already closed, and then changes nothing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(closing: DayClosingEntity): Long

    @Query("SELECT * FROM day_closings WHERE business_date = :date")
    suspend fun forDate(date: String): DayClosingEntity?

    @Query("SELECT * FROM day_closings WHERE id = :id")
    suspend fun byId(id: Long): DayClosingEntity?

    @Query("SELECT * FROM day_closings ORDER BY business_date DESC")
    fun observeAll(): Flow<List<DayClosingEntity>>

    @Query("SELECT * FROM day_closings WHERE business_date = :date")
    fun observeForDate(date: String): Flow<DayClosingEntity?>

    /** Payments and refunds saved with bills and credit notes between two instants, per method and kind. */
    @Query(
        "SELECT method AS method, kind AS kind, COALESCE(SUM(amount_minor), 0) AS total_minor FROM payments " +
            "WHERE created_at >= :fromMillis AND created_at < :toMillis GROUP BY method, kind",
    )
    suspend fun paymentTotals(fromMillis: Long, toMillis: Long): List<PaymentTotalRow>

    /** Money customers paid against their accounts (the ledger holds those as negative amounts). */
    @Query(
        "SELECT method AS method, COALESCE(SUM(-amount_minor), 0) AS total_minor FROM customer_ledger " +
            "WHERE type = 'PAYMENT' AND created_at >= :fromMillis AND created_at < :toMillis GROUP BY method",
    )
    suspend fun accountReceiptTotals(fromMillis: Long, toMillis: Long): List<ReceiptTotalRow>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'SALE' THEN 1 ELSE 0 END), 0) AS bills, " +
            "COALESCE(SUM(CASE WHEN type = 'RETURN' THEN 1 ELSE 0 END), 0) AS returns, " +
            "COALESCE(SUM(CASE WHEN type = 'SALE' THEN grand_total_minor ELSE -grand_total_minor END), 0) AS net_minor " +
            "FROM invoices WHERE created_at >= :fromMillis AND created_at < :toMillis",
    )
    suspend fun counts(fromMillis: Long, toMillis: Long): DayCountsRow
}
