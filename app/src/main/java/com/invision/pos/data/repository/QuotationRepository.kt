package com.invision.pos.data.repository

import androidx.room.withTransaction
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.local.entity.QuotationEntity
import com.invision.pos.domain.billing.BusinessTime
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.InvoiceNumbers
import com.invision.pos.domain.billing.Quotation
import com.invision.pos.domain.billing.QuotationStatus
import com.invision.pos.domain.billing.QuotationSummary
import com.invision.pos.domain.billing.Quotations
import com.invision.pos.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Quotations. Saving, listing, deleting and converting a quotation never changes stock, an invoice number or a
 * customer account: those change only when the bill made from it is paid as a normal sale.
 */
interface QuotationRepository {
    /** Newest first. A quotation that cannot be read any more is still listed so it can be deleted. */
    fun observeAll(): Flow<List<QuotationSummary>>

    /** Saves [cart] as a new quotation valid until [validUntil], and returns it with its number. */
    suspend fun save(cart: Cart, total: Money, validUntil: LocalDate): QuotationSummary

    /** The quotation, or null if it is gone or cannot be read. */
    suspend fun get(id: Long): Quotation?

    suspend fun delete(id: Long)

    /** Records that [id] became invoice [invoiceId]. Returns false if it was already used or is gone. */
    suspend fun markConverted(id: Long, invoiceId: Long): Boolean
}

class RoomQuotationRepository(private val database: InvisionDatabase, private val clock: Clock) : QuotationRepository {
    private val dao = database.quotationDao()
    private val operations = database.operationDao()

    override fun observeAll(): Flow<List<QuotationSummary>> = dao.observeAll().map { rows -> rows.map { it.toSummary() } }

    override suspend fun save(cart: Cart, total: Money, validUntil: LocalDate): QuotationSummary {
        require(!cart.isEmpty) { "Nothing to quote" }
        return database.withTransaction {
            val now = clock.millis()
            val day = Instant.ofEpochMilli(now).atZone(BusinessTime.ZONE).toLocalDate()
            val year = InvoiceNumbers.financialYearStart(day)
            val number = operations.nextNumber(Quotations.sequenceKey(year)) { Quotations.number(year, it) }
            val entity = QuotationEntity(
                number = number,
                createdAt = now,
                validUntilDay = validUntil.toEpochDay(),
                customerName = cart.customer.name.trim(),
                customerPhone = cart.customer.phone.trim(),
                itemCount = cart.itemCount,
                totalMinor = total.minorUnits,
                payload = HeldCartCodec.encode(cart),
                status = QuotationStatus.OPEN.name,
            )
            entity.copy(id = dao.insert(entity)).toSummary()
        }
    }

    override suspend fun get(id: Long): Quotation? {
        val row = dao.get(id) ?: return null
        val cart = HeldCartCodec.decode(row.payload) ?: return null
        return Quotation(row.toSummary(), cart)
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun markConverted(id: Long, invoiceId: Long): Boolean = dao.markConverted(id, invoiceId) > 0

    private fun QuotationEntity.toSummary() = QuotationSummary(
        id = id,
        number = number,
        createdAtMillis = createdAt,
        validUntil = LocalDate.ofEpochDay(validUntilDay),
        customerName = customerName,
        customerPhone = customerPhone,
        itemCount = itemCount,
        total = Money(totalMinor),
        status = QuotationStatus.entries.firstOrNull { it.name == status } ?: QuotationStatus.OPEN,
        convertedInvoiceId = convertedInvoiceId,
    )
}
