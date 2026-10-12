package com.invision.pos.data.repository

import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceSummary
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.StoreSettings
import kotlinx.coroutines.flow.Flow

sealed interface SaleResult {
    data class Recorded(val invoiceId: Long, val number: String) : SaleResult

    /** Nothing was saved: these variants are missing or don't have enough unexpired stock. */
    data class StockShortage(val variantIds: Set<Long>) : SaleResult

    /** Nothing was saved: dishes need more of these ingredients than are in stock. */
    data class IngredientShortage(val variantIds: Set<Long>) : SaleResult

    /** Nothing was saved: the amount on account would go past the customer's credit limit. */
    data object CreditLimitExceeded : SaleResult

    /** Nothing was saved: the customer account no longer exists. */
    data object CustomerMissing : SaleResult

    /**
     * Nothing was saved: the loyalty points on the bill cannot be used. Loyalty was switched off, the customer
     * has fewer points than the bill uses, or the discount does not match the points.
     */
    data object LoyaltyUnavailable : SaleResult
}

sealed interface ReturnResult {
    data class Recorded(val creditNoteId: Long, val number: String) : ReturnResult

    /** Nothing was saved: a line would return more units than were sold. */
    data object QuantityExceedsSold : ReturnResult
}

data class SalesTotals(val net: Money, val billCount: Int)

interface BillingRepository {
    fun observeInvoice(invoiceId: Long): Flow<Invoice?>

    suspend fun getInvoice(invoiceId: Long): Invoice?

    /** Invoices and credit notes created in [fromMillis, toMillis), newest first. */
    fun observeInvoices(fromMillis: Long, toMillis: Long): Flow<List<InvoiceSummary>>

    /** Credit notes issued against a sale. */
    fun observeCreditNotesFor(invoiceId: Long): Flow<List<InvoiceSummary>>

    /** Payments and refunds recorded in [fromMillis, toMillis), newest first. */
    fun observePayments(fromMillis: Long, toMillis: Long): Flow<List<PaymentRecord>>

    fun observeSalesTotals(fromMillis: Long, toMillis: Long): Flow<SalesTotals>

    /**
     * Saves a sale built by SaleBuilder in one transaction: invoice, payments, stock
     * (earliest expiry first, never expired stock), ingredients used by dishes, and
     * the amount put on the customer's account. A [requestId] already used returns
     * the sale saved the first time instead of saving again.
     */
    suspend fun recordSale(invoice: Invoice, invoicePrefix: String, requestId: String? = null): SaleResult

    /**
     * The number the next sale will get, for showing on the payment screen (the UPI QR carries it). Nothing is
     * reserved: the number is only taken when the sale is saved, so this can differ if another sale is saved first.
     */
    suspend fun previewInvoiceNumber(invoicePrefix: String, day: java.time.LocalDate): String

    /** Full invoices and credit notes in [fromMillis, toMillis), oldest first, for reports. */
    suspend fun invoicesBetween(fromMillis: Long, toMillis: Long): List<Invoice>

    /**
     * Saves a credit note built by ReturnBuilder in one transaction: puts the units
     * back into the batches they were sold from and credits refunds made to the
     * customer's account. Nothing is saved unless the lines belong to the sale, no more
     * comes back than was sold and the refunds stay within what the sale was for.
     *
     * A repeated [requestId] returns the credit note saved the first time instead of
     * saving another.
     */
    suspend fun recordReturn(creditNote: Invoice, creditNotePrefix: String, requestId: String? = null): ReturnResult
}

interface SettingsRepository {
    /** Emits defaults until settings are saved for the first time. */
    fun observeSettings(): Flow<StoreSettings>

    suspend fun getSettings(): StoreSettings

    suspend fun saveSettings(settings: StoreSettings)
}
