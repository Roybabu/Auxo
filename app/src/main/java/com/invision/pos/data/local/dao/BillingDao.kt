package com.invision.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.invision.pos.data.local.entity.InvoiceEntity
import com.invision.pos.data.local.entity.InvoiceLineEntity
import com.invision.pos.data.local.entity.InvoiceSummaryRow
import com.invision.pos.data.local.entity.InvoiceWithDetails
import com.invision.pos.data.local.entity.NumberSequenceEntity
import com.invision.pos.data.local.entity.PaymentEntity
import com.invision.pos.data.local.entity.PaymentWithInvoice
import com.invision.pos.data.local.entity.SalesTotalsRow
import com.invision.pos.data.local.entity.StockMovementEntity
import com.invision.pos.data.local.entity.StoreSettingsEntity
import kotlinx.coroutines.flow.Flow

/** Thrown inside a sale transaction to roll it back. */
class StockShortageException(val variantIds: Set<Long>) : RuntimeException("Not enough stock")

/** Thrown inside a return transaction to roll it back. */
class ReturnQuantityException : RuntimeException("Return exceeds units sold")

@Dao
abstract class BillingDao {

    // Settings

    @Query("SELECT * FROM store_settings WHERE id = 1")
    abstract fun observeSettings(): Flow<StoreSettingsEntity?>

    @Query("SELECT * FROM store_settings WHERE id = 1")
    abstract suspend fun getSettings(): StoreSettingsEntity?

    @Upsert
    abstract suspend fun upsertSettings(settings: StoreSettingsEntity)

    // Reading invoices

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :invoiceId")
    abstract fun observeInvoice(invoiceId: Long): Flow<InvoiceWithDetails?>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :invoiceId")
    abstract suspend fun getInvoice(invoiceId: Long): InvoiceWithDetails?

    @Query(
        "SELECT i.id AS id, i.number AS number, i.type AS type, i.created_at AS created_at, i.buyer_name AS buyer_name, " +
            "i.buyer_phone AS buyer_phone, i.grand_total_minor AS grand_total_minor, " +
            "(SELECT COALESCE(SUM(l.quantity), 0) FROM invoice_lines l WHERE l.invoice_id = i.id) AS item_count " +
            "FROM invoices i WHERE i.created_at >= :fromMillis AND i.created_at < :toMillis " +
            "ORDER BY i.created_at DESC, i.id DESC",
    )
    abstract fun observeInvoiceSummaries(fromMillis: Long, toMillis: Long): Flow<List<InvoiceSummaryRow>>

    @Query(
        "SELECT i.id AS id, i.number AS number, i.type AS type, i.created_at AS created_at, i.buyer_name AS buyer_name, " +
            "i.buyer_phone AS buyer_phone, i.grand_total_minor AS grand_total_minor, " +
            "(SELECT COALESCE(SUM(l.quantity), 0) FROM invoice_lines l WHERE l.invoice_id = i.id) AS item_count " +
            "FROM invoices i WHERE i.original_invoice_id = :invoiceId ORDER BY i.created_at, i.id",
    )
    abstract fun observeCreditNotesFor(invoiceId: Long): Flow<List<InvoiceSummaryRow>>

    @Query(
        "SELECT p.*, i.number AS invoice_number FROM payments p JOIN invoices i ON i.id = p.invoice_id " +
            "WHERE p.created_at >= :fromMillis AND p.created_at < :toMillis ORDER BY p.created_at DESC, p.id DESC",
    )
    abstract fun observePayments(fromMillis: Long, toMillis: Long): Flow<List<PaymentWithInvoice>>

    /** Sales minus returns, and the number of sales, between two instants. */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'SALE' THEN grand_total_minor ELSE -grand_total_minor END), 0) AS net_minor, " +
            "COALESCE(SUM(CASE WHEN type = 'SALE' THEN 1 ELSE 0 END), 0) AS bill_count " +
            "FROM invoices WHERE created_at >= :fromMillis AND created_at < :toMillis",
    )
    abstract fun observeSalesTotals(fromMillis: Long, toMillis: Long): Flow<SalesTotalsRow>

    // Writing

    @Insert
    abstract suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert
    abstract suspend fun insertLines(lines: List<InvoiceLineEntity>)

    @Insert
    abstract suspend fun insertPayments(payments: List<PaymentEntity>)

    @Insert
    abstract suspend fun insertMovement(movement: StockMovementEntity): Long

    @Query("SELECT next_value FROM number_sequences WHERE name = :name")
    abstract suspend fun sequenceValue(name: String): Long?

    @Upsert
    abstract suspend fun upsertSequence(sequence: NumberSequenceEntity)

    @Query("SELECT id, stock_quantity FROM product_variants WHERE id IN (:variantIds)")
    abstract suspend fun stockLevels(variantIds: List<Long>): List<VariantStock>

    @Query(
        "UPDATE product_variants SET stock_quantity = stock_quantity - :quantity " +
            "WHERE id = :variantId AND stock_quantity >= :quantity",
    )
    abstract suspend fun takeStock(variantId: Long, quantity: Int): Int

    @Query("UPDATE product_variants SET stock_quantity = stock_quantity + :quantity WHERE id = :variantId")
    abstract suspend fun returnStock(variantId: Long, quantity: Int): Int

    @Query(
        "UPDATE invoice_lines SET returned_quantity = returned_quantity + :quantity " +
            "WHERE id = :lineId AND returned_quantity + :quantity <= quantity",
    )
    abstract suspend fun markReturned(lineId: Long, quantity: Int): Int

    /**
     * Saves a sale in one transaction: checks and deducts stock, numbers the
     * invoice, and stores lines, payments and stock movements. If any variant is
     * missing or short of stock, nothing is saved and [StockShortageException] is thrown.
     *
     * @return the new invoice id
     */
    @Transaction
    open suspend fun recordSale(
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        payments: List<PaymentEntity>,
        sequenceName: String,
        formatNumber: (Long) -> String,
    ): Long {
        val wanted = lines.filter { it.variantId != null }
            .groupBy { it.variantId!! }
            .mapValues { (_, group) -> group.sumOf { it.quantity } }
        val stock = stockLevels(wanted.keys.toList()).associate { it.id to it.stockQuantity }
        val short = wanted.filter { (variantId, quantity) -> (stock[variantId] ?: -1) < quantity }.keys
        if (short.isNotEmpty()) throw StockShortageException(short)
        for ((variantId, quantity) in wanted) {
            if (takeStock(variantId, quantity) == 0) throw StockShortageException(setOf(variantId))
        }

        val number = nextNumber(sequenceName, formatNumber)
        val invoiceId = insertInvoice(invoice.copy(number = number))
        insertLines(lines.map { it.copy(invoiceId = invoiceId) })
        insertPayments(payments.map { it.copy(invoiceId = invoiceId) })
        for ((variantId, quantity) in wanted) {
            insertMovement(
                StockMovementEntity(
                    variantId = variantId,
                    quantityChange = -quantity,
                    reason = "SOLD",
                    note = number,
                    createdAt = invoice.createdAt,
                    invoiceId = invoiceId,
                ),
            )
        }
        return invoiceId
    }

    /**
     * Saves a credit note in one transaction: marks units as returned on the
     * original lines, puts them back in stock and records the refund. If any line
     * would return more than was sold, nothing is saved and [ReturnQuantityException] is thrown.
     *
     * @return the new credit note id
     */
    @Transaction
    open suspend fun recordReturn(
        creditNote: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        refunds: List<PaymentEntity>,
        sequenceName: String,
        formatNumber: (Long) -> String,
    ): Long {
        for (line in lines) {
            val originalLineId = line.originalLineId ?: throw ReturnQuantityException()
            if (markReturned(originalLineId, line.quantity) == 0) throw ReturnQuantityException()
        }

        val number = nextNumber(sequenceName, formatNumber)
        val creditNoteId = insertInvoice(creditNote.copy(number = number))
        insertLines(lines.map { it.copy(invoiceId = creditNoteId, returnedQuantity = 0) })
        insertPayments(refunds.map { it.copy(invoiceId = creditNoteId) })

        for (line in lines) {
            val variantId = line.variantId ?: continue
            // Skipped if the product was deleted after the sale.
            if (returnStock(variantId, line.quantity) == 1) {
                insertMovement(
                    StockMovementEntity(
                        variantId = variantId,
                        quantityChange = line.quantity,
                        reason = "RETURNED",
                        note = number,
                        createdAt = creditNote.createdAt,
                        invoiceId = creditNoteId,
                    ),
                )
            }
        }
        return creditNoteId
    }

    private suspend fun nextNumber(sequenceName: String, formatNumber: (Long) -> String): String {
        val value = sequenceValue(sequenceName) ?: 1L
        upsertSequence(NumberSequenceEntity(sequenceName, value + 1))
        return formatNumber(value)
    }
}

data class VariantStock(
    val id: Long,
    @androidx.room.ColumnInfo(name = "stock_quantity") val stockQuantity: Int,
)
