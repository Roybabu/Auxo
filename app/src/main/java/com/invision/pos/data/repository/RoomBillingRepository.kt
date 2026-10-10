package com.invision.pos.data.repository

import com.invision.pos.data.local.dao.BillingDao
import com.invision.pos.data.local.dao.ReturnQuantityException
import com.invision.pos.data.local.dao.StockShortageException
import com.invision.pos.data.local.toDomain
import com.invision.pos.data.local.toEntity
import com.invision.pos.domain.billing.InvoiceNumbers
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceSummary
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.StoreSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

class RoomBillingRepository(
    private val dao: BillingDao,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : BillingRepository {

    override fun observeInvoice(invoiceId: Long): Flow<Invoice?> = dao.observeInvoice(invoiceId).map { it?.toDomain() }

    override suspend fun getInvoice(invoiceId: Long): Invoice? = dao.getInvoice(invoiceId)?.toDomain()

    override fun observeInvoices(fromMillis: Long, toMillis: Long): Flow<List<InvoiceSummary>> =
        dao.observeInvoiceSummaries(fromMillis, toMillis).map { rows -> rows.map { it.toDomain() } }

    override fun observeCreditNotesFor(invoiceId: Long): Flow<List<InvoiceSummary>> =
        dao.observeCreditNotesFor(invoiceId).map { rows -> rows.map { it.toDomain() } }

    override fun observePayments(fromMillis: Long, toMillis: Long): Flow<List<PaymentRecord>> =
        dao.observePayments(fromMillis, toMillis).map { rows -> rows.map { it.toDomain() } }

    override fun observeSalesTotals(fromMillis: Long, toMillis: Long): Flow<SalesTotals> =
        dao.observeSalesTotals(fromMillis, toMillis).map { SalesTotals(Money(it.netMinor), it.billCount) }

    override suspend fun recordSale(invoice: Invoice, invoicePrefix: String): SaleResult {
        require(invoice.type == InvoiceType.SALE && invoice.id == 0L)
        val year = financialYear(invoice.createdAtMillis)
        return try {
            val id = dao.recordSale(
                invoice = invoice.toEntity(),
                lines = invoice.lines.map { it.toEntity(invoiceId = 0) },
                payments = invoice.payments.map { it.toEntity(invoiceId = 0) },
                sequenceName = InvoiceNumbers.sequenceKey(invoicePrefix, year),
                formatNumber = { InvoiceNumbers.format(invoicePrefix, year, it) },
            )
            SaleResult.Recorded(id, dao.getInvoice(id)?.invoice?.number.orEmpty())
        } catch (e: StockShortageException) {
            SaleResult.StockShortage(e.variantIds)
        }
    }

    override suspend fun recordReturn(creditNote: Invoice, creditNotePrefix: String): ReturnResult {
        require(creditNote.type == InvoiceType.RETURN && creditNote.id == 0L)
        val year = financialYear(creditNote.createdAtMillis)
        return try {
            val id = dao.recordReturn(
                creditNote = creditNote.toEntity(),
                lines = creditNote.lines.map { it.toEntity(invoiceId = 0) },
                refunds = creditNote.payments.map { it.toEntity(invoiceId = 0) },
                sequenceName = InvoiceNumbers.sequenceKey(creditNotePrefix, year),
                formatNumber = { InvoiceNumbers.format(creditNotePrefix, year, it) },
            )
            ReturnResult.Recorded(id, dao.getInvoice(id)?.invoice?.number.orEmpty())
        } catch (e: ReturnQuantityException) {
            ReturnResult.QuantityExceedsSold
        }
    }

    private fun financialYear(epochMillis: Long): Int =
        InvoiceNumbers.financialYearStart(Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate())
}

class RoomSettingsRepository(private val dao: BillingDao) : SettingsRepository {

    override fun observeSettings(): Flow<StoreSettings> = dao.observeSettings().map { it?.toDomain() ?: StoreSettings() }

    override suspend fun getSettings(): StoreSettings = dao.getSettings()?.toDomain() ?: StoreSettings()

    override suspend fun saveSettings(settings: StoreSettings) {
        dao.upsertSettings(settings.toEntity())
    }
}
