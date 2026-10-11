package com.invision.pos.testutil

import com.invision.pos.data.repository.BillingRepository
import com.invision.pos.data.repository.ReturnResult
import com.invision.pos.data.repository.SaleResult
import com.invision.pos.data.repository.SalesTotals
import com.invision.pos.data.repository.SettingsRepository
import com.invision.pos.domain.billing.InvoiceNumbers
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceSummary
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.domain.model.StoreSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset

class FakeSettingsRepository(initial: StoreSettings = TestData.settings) : SettingsRepository {
    val settings = MutableStateFlow(initial)
    var failSaves = false

    override fun observeSettings(): Flow<StoreSettings> = settings

    override suspend fun getSettings(): StoreSettings = settings.value

    override suspend fun saveSettings(settings: StoreSettings) {
        if (failSaves) throw IOException("Simulated write failure")
        this.settings.value = settings
    }
}

/**
 * In-memory billing store that, like the Room implementation, deducts and
 * restores stock in [inventory] and rejects sales when stock is short.
 */
class FakeBillingRepository(private val inventory: FakeInventoryRepository) : BillingRepository {

    val invoices = MutableStateFlow<List<Invoice>>(emptyList())
    private var nextId = 1L
    private var nextLineId = 1L
    private val sequences = mutableMapOf<String, Long>()
    private val requests = mutableMapOf<String, SaleResult>()

    override fun observeInvoice(invoiceId: Long): Flow<Invoice?> = invoices.map { list -> list.firstOrNull { it.id == invoiceId } }

    override suspend fun getInvoice(invoiceId: Long): Invoice? = invoices.value.firstOrNull { it.id == invoiceId }

    override fun observeInvoices(fromMillis: Long, toMillis: Long): Flow<List<InvoiceSummary>> = invoices.map { list ->
        list.filter { it.createdAtMillis in fromMillis until toMillis }
            .sortedByDescending { it.createdAtMillis }
            .map { it.summary() }
    }

    override fun observeCreditNotesFor(invoiceId: Long): Flow<List<InvoiceSummary>> =
        invoices.map { list -> list.filter { it.originalInvoiceId == invoiceId }.map { it.summary() } }

    override fun observePayments(fromMillis: Long, toMillis: Long): Flow<List<PaymentRecord>> = invoices.map { list ->
        list.flatMap { invoice -> invoice.payments.map { it.copy(invoiceId = invoice.id, invoiceNumber = invoice.number) } }
            .filter { it.createdAtMillis in fromMillis until toMillis }
    }

    override fun observeSalesTotals(fromMillis: Long, toMillis: Long): Flow<SalesTotals> = invoices.map { list ->
        val inRange = list.filter { it.createdAtMillis in fromMillis until toMillis }
        val net = inRange.fold(Money.ZERO) { acc, i -> if (i.type == InvoiceType.SALE) acc + i.grandTotal else acc - i.grandTotal }
        SalesTotals(net, inRange.count { it.type == InvoiceType.SALE })
    }

    override suspend fun invoicesBetween(fromMillis: Long, toMillis: Long): List<Invoice> =
        invoices.value.filter { it.createdAtMillis in fromMillis until toMillis }.sortedBy { it.createdAtMillis }

    override suspend fun recordSale(invoice: Invoice, invoicePrefix: String, requestId: String?): SaleResult {
        requestId?.let { id -> requests[id]?.let { return it } }
        val result = saveSale(invoice, invoicePrefix)
        if (requestId != null && result is SaleResult.Recorded) requests[requestId] = result
        return result
    }

    private suspend fun saveSale(invoice: Invoice, invoicePrefix: String): SaleResult {
        val stock = inventory.products.value.flatMap { it.variants }.associate { it.id to it.stockQuantity }
        val wanted = invoice.lines.filter { it.variantId != null }.groupBy { it.variantId!! }.mapValues { (_, l) -> l.sumOf { it.quantity } }
        val short = wanted.filter { (id, qty) -> (stock[id] ?: -1) < qty }.keys
        if (short.isNotEmpty()) return SaleResult.StockShortage(short)
        val number = nextNumber(invoicePrefix, invoice.createdAtMillis)
        val id = nextId++
        wanted.forEach { (variantId, qty) ->
            inventory.recordStockChange(StockMovement(variantId = variantId, quantityChange = -qty, reason = StockChangeReason.SOLD, createdAtMillis = invoice.createdAtMillis))
        }
        val saved = invoice.copy(
            id = id,
            number = number,
            lines = invoice.lines.map { it.copy(id = nextLineId++) },
            payments = invoice.payments.map { it.copy(invoiceId = id) },
        )
        invoices.value += saved
        return SaleResult.Recorded(id, number)
    }

    private val returnRequests = mutableMapOf<String, ReturnResult>()

    override suspend fun recordReturn(creditNote: Invoice, creditNotePrefix: String, requestId: String?): ReturnResult {
        requestId?.let { id -> returnRequests[id]?.let { return it } }
        val result = saveReturn(creditNote, creditNotePrefix)
        if (requestId != null && result is ReturnResult.Recorded) returnRequests[requestId] = result
        return result
    }

    private suspend fun saveReturn(creditNote: Invoice, creditNotePrefix: String): ReturnResult {
        val original = invoices.value.first { it.id == creditNote.originalInvoiceId }
        for (line in creditNote.lines) {
            val sold = original.lines.first { it.id == line.originalLineId }
            if (sold.returnedQuantity + line.quantity > sold.quantity) return ReturnResult.QuantityExceedsSold
        }
        val number = nextNumber(creditNotePrefix, creditNote.createdAtMillis)
        val id = nextId++
        val updatedOriginal = original.copy(lines = original.lines.map { sold ->
            val returned = creditNote.lines.filter { it.originalLineId == sold.id }.sumOf { it.quantity }
            sold.copy(returnedQuantity = sold.returnedQuantity + returned)
        })
        creditNote.lines.forEach { line ->
            line.variantId?.let {
                inventory.recordStockChange(StockMovement(variantId = it, quantityChange = line.quantity, reason = StockChangeReason.RETURNED, createdAtMillis = creditNote.createdAtMillis))
            }
        }
        invoices.value = invoices.value.map { if (it.id == original.id) updatedOriginal else it } +
            creditNote.copy(id = id, number = number, lines = creditNote.lines.map { it.copy(id = nextLineId++) })
        return ReturnResult.Recorded(id, number)
    }

    private fun nextNumber(prefix: String, millis: Long): String {
        val year = InvoiceNumbers.financialYearStart(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        val key = InvoiceNumbers.sequenceKey(prefix, year)
        val value = sequences[key] ?: 1L
        sequences[key] = value + 1
        return InvoiceNumbers.format(prefix, year, value)
    }

    private fun Invoice.summary() = InvoiceSummary(id, number, type, createdAtMillis, buyer.name, buyer.phone, grandTotal, itemCount)
}
