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
import com.invision.pos.data.local.entity.PaymentEntity
import com.invision.pos.data.local.entity.PaymentWithInvoice
import com.invision.pos.data.local.entity.SalesTotalsRow
import com.invision.pos.data.local.entity.StoreSettingsEntity
import kotlinx.coroutines.flow.Flow

/** Thrown inside a transaction to roll it back when items are short of stock. */
class StockShortageException(val variantIds: Set<Long>) : RuntimeException("Not enough stock")

/** Thrown inside a return transaction to roll it back. */
class ReturnQuantityException : RuntimeException("Return exceeds units sold")

/** Thrown when a dish's recipe needs more of an ingredient than is in stock. */
class IngredientShortageException(val variantIds: Set<Long>) : RuntimeException("Not enough ingredients")

/** Thrown when a sale on account would take the customer past their credit limit. */
class CreditLimitException : RuntimeException("Credit limit exceeded")

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

    /** Full invoices and credit notes between two instants, oldest first, for reports. */
    @Transaction
    @Query("SELECT * FROM invoices WHERE created_at >= :fromMillis AND created_at < :toMillis ORDER BY created_at, id")
    abstract suspend fun invoicesBetween(fromMillis: Long, toMillis: Long): List<InvoiceWithDetails>

    @Query(
        "SELECT i.id AS id, i.number AS number, i.type AS type, i.created_at AS created_at, i.buyer_name AS buyer_name, " +
            "i.buyer_phone AS buyer_phone, i.grand_total_minor AS grand_total_minor, " +
            "(SELECT COALESCE(SUM(l.quantity), 0) FROM invoice_lines l WHERE l.invoice_id = i.id) AS item_count " +
            "FROM invoices i WHERE i.customer_id = :customerId ORDER BY i.created_at DESC, i.id DESC",
    )
    abstract fun observeInvoicesForCustomer(customerId: Long): Flow<List<InvoiceSummaryRow>>

    // Writing

    @Insert
    abstract suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert
    abstract suspend fun insertLines(lines: List<InvoiceLineEntity>)

    @Insert
    abstract suspend fun insertPayments(payments: List<PaymentEntity>)

    /**
     * Counts [quantity] more units of a line of sale [saleId] as returned. Matches nothing, and so
     * changes nothing, if the line belongs to another invoice, the invoice is not a sale, or
     * more would come back than was sold.
     */
    @Query(
        "UPDATE invoice_lines SET returned_quantity = returned_quantity + :quantity " +
            "WHERE id = :lineId AND invoice_id = :saleId AND :quantity > 0 AND returned_quantity + :quantity <= quantity " +
            "AND EXISTS (SELECT 1 FROM invoices s WHERE s.id = :saleId AND s.type = 'SALE')",
    )
    abstract suspend fun markReturned(saleId: Long, lineId: Long, quantity: Int): Int

    /** Total of the credit notes already issued against sale [saleId]. */
    @Query("SELECT COALESCE(SUM(grand_total_minor), 0) FROM invoices WHERE original_invoice_id = :saleId AND type = 'RETURN'")
    abstract suspend fun creditedTotal(saleId: Long): Long

    @Query("SELECT grand_total_minor FROM invoices WHERE id = :invoiceId")
    abstract suspend fun grandTotalOf(invoiceId: Long): Long?

    @Query("SELECT customer_id FROM invoices WHERE id = :invoiceId")
    abstract suspend fun customerOf(invoiceId: Long): Long?
}
