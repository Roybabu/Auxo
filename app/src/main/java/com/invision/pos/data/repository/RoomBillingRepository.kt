package com.invision.pos.data.repository

import com.invision.pos.domain.billing.BusinessTime
import androidx.room.withTransaction
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.local.MovementInfo
import com.invision.pos.data.local.RequestClaim
import com.invision.pos.data.local.StockWriter
import com.invision.pos.data.local.claim
import com.invision.pos.data.local.dao.CreditLimitException
import com.invision.pos.data.local.dao.IngredientShortageException
import com.invision.pos.data.local.dao.ReturnQuantityException
import com.invision.pos.data.local.dao.StockShortageException
import com.invision.pos.data.local.entity.LedgerEntryEntity
import com.invision.pos.data.local.toDomain
import com.invision.pos.data.local.toEntity
import com.invision.pos.domain.billing.InvoiceNumbers
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceSummary
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.LedgerEntryType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StoreSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Thrown inside a sale transaction when its customer account no longer exists. */
private class CustomerMissingException : RuntimeException("Customer not found")

class RoomBillingRepository(
    private val database: InvisionDatabase,
    private val zone: () -> ZoneId = { BusinessTime.ZONE },
) : BillingRepository {

    private val dao = database.billingDao()
    private val operations = database.operationDao()
    private val customers = database.customerDao()
    private val menu = database.menuDao()
    private val stock = StockWriter(database.inventoryDao())

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

    override suspend fun invoicesBetween(fromMillis: Long, toMillis: Long): List<Invoice> =
        dao.invoicesBetween(fromMillis, toMillis).map { it.toDomain() }

    override suspend fun recordSale(invoice: Invoice, invoicePrefix: String, requestId: String?): SaleResult {
        require(invoice.type == InvoiceType.SALE && invoice.id == 0L)
        val saleDay = localDate(invoice.createdAtMillis)
        val year = InvoiceNumbers.financialYearStart(saleDay)
        return try {
            database.withTransaction {
                val claim = requestId?.let { operations.claim(it, "SALE", invoice.createdAtMillis) }
                // A repeated request never bills twice; it gets the first invoice back.
                if (claim is RequestClaim.AlreadyDone) {
                    val saved = claim.resultId?.let { dao.getInvoice(it) }
                    return@withTransaction SaleResult.Recorded(saved?.invoice?.id ?: 0L, saved?.invoice?.number.orEmpty())
                }

                // Check everything first, so every short item is reported at once.
                val wanted = invoice.lines.mapNotNull { line -> line.variantId?.let { it to line.quantity } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, quantities) -> quantities.sum() }
                val short = wanted.filter { (variantId, quantity) -> stock.available(variantId, saleDay) < quantity }.keys
                if (short.isNotEmpty()) throw StockShortageException(short)
                val ingredients = ingredientsFor(invoice)
                val shortIngredients = ingredients.filter { (variantId, quantity) -> stock.available(variantId, saleDay) < quantity }.keys
                if (shortIngredients.isNotEmpty()) throw IngredientShortageException(shortIngredients)
                val onAccount = invoice.payments.filter { it.method == PaymentMethod.CREDIT }
                    .fold(Money.ZERO) { acc, payment -> acc + payment.amount }
                if (!onAccount.isZero) checkCredit(invoice.customerId, onAccount)

                val number = operations.nextNumber(InvoiceNumbers.sequenceKey(invoicePrefix, year)) {
                    InvoiceNumbers.format(invoicePrefix, year, it)
                }
                val invoiceId = dao.insertInvoice(invoice.copy(number = number).toEntity())
                dao.insertLines(invoice.lines.map { it.toEntity(invoiceId) })
                dao.insertPayments(invoice.payments.map { it.toEntity(invoiceId) })

                val sold = MovementInfo(StockChangeReason.SOLD, invoice.createdAtMillis, note = number, invoiceId = invoiceId)
                for ((variantId, quantity) in wanted) stock.remove(variantId, quantity, sold, sellableOn = saleDay)
                val used = MovementInfo(StockChangeReason.USED, invoice.createdAtMillis, note = number, invoiceId = invoiceId)
                for ((variantId, quantity) in ingredients) stock.remove(variantId, quantity, used, sellableOn = saleDay)

                if (!onAccount.isZero) {
                    customers.insertEntry(
                        LedgerEntryEntity(
                            customerId = invoice.customerId!!,
                            type = LedgerEntryType.SALE.name,
                            amountMinor = onAccount.minorUnits,
                            invoiceId = invoiceId,
                            method = null,
                            reference = null,
                            note = null,
                            createdAt = invoice.createdAtMillis,
                        ),
                    )
                }
                if (requestId != null) operations.setRequestResult(requestId, invoiceId)
                SaleResult.Recorded(invoiceId, number)
            }
        } catch (e: StockShortageException) {
            SaleResult.StockShortage(e.variantIds)
        } catch (e: IngredientShortageException) {
            SaleResult.IngredientShortage(e.variantIds)
        } catch (e: CreditLimitException) {
            SaleResult.CreditLimitExceeded
        } catch (e: CustomerMissingException) {
            SaleResult.CustomerMissing
        }
    }

    /** Ingredients needed by the dishes on [invoice] whose recipes deduct stock, per variant. */
    private suspend fun ingredientsFor(invoice: Invoice): Map<Long, Int> {
        val dishes = invoice.lines.mapNotNull { line -> line.menuItemId?.let { it to line.quantity } }
        if (dishes.isEmpty()) return emptyMap()
        val recipes = menu.getItems(dishes.map { it.first }.distinct()).associateBy { it.item.id }
        val needed = mutableMapOf<Long, Long>()
        for ((menuItemId, servings) in dishes) {
            val dish = recipes[menuItemId] ?: continue
            if (!dish.item.deductIngredients) continue
            for (line in dish.recipe) {
                needed[line.variantId] = (needed[line.variantId] ?: 0L) + line.quantity.toLong() * servings
            }
        }
        if (needed.values.any { it > Int.MAX_VALUE }) throw IngredientShortageException(needed.filterValues { it > Int.MAX_VALUE }.keys)
        return needed.mapValues { it.value.toInt() }
    }

    /** Throws unless [customerId] exists and can take [amount] more on account. */
    private suspend fun checkCredit(customerId: Long?, amount: Money) {
        val customer = customerId?.let { customers.getCustomer(it) } ?: throw CustomerMissingException()
        val limit = customer.creditLimitMinor ?: return
        if (customers.balance(customer.id) + amount.minorUnits > limit) throw CreditLimitException()
    }

    override suspend fun recordReturn(creditNote: Invoice, creditNotePrefix: String): ReturnResult {
        require(creditNote.type == InvoiceType.RETURN && creditNote.id == 0L)
        val year = InvoiceNumbers.financialYearStart(localDate(creditNote.createdAtMillis))
        val saleId = requireNotNull(creditNote.originalInvoiceId) { "A credit note needs the sale it returns" }
        return try {
            database.withTransaction {
                for (line in creditNote.lines) {
                    val originalLineId = line.originalLineId ?: throw ReturnQuantityException()
                    if (dao.markReturned(originalLineId, line.quantity) == 0) throw ReturnQuantityException()
                }

                val number = operations.nextNumber(InvoiceNumbers.sequenceKey(creditNotePrefix, year)) {
                    InvoiceNumbers.format(creditNotePrefix, year, it)
                }
                val customerId = creditNote.customerId ?: dao.customerOf(saleId)
                val creditNoteId = dao.insertInvoice(creditNote.copy(number = number, customerId = customerId).toEntity())
                dao.insertLines(creditNote.lines.map { it.toEntity(creditNoteId).copy(returnedQuantity = 0) })
                dao.insertPayments(creditNote.payments.map { it.toEntity(creditNoteId) })

                val info = MovementInfo(StockChangeReason.RETURNED, creditNote.createdAtMillis, note = number, invoiceId = creditNoteId)
                for (line in creditNote.lines) {
                    val variantId = line.variantId ?: continue
                    stock.restoreFromSale(saleId, variantId, line.quantity, info)
                }

                val toAccount = creditNote.payments.filter { it.method == PaymentMethod.CREDIT }
                    .fold(Money.ZERO) { acc, payment -> acc + payment.amount }
                if (!toAccount.isZero) {
                    customers.insertEntry(
                        LedgerEntryEntity(
                            customerId = customerId ?: throw ReturnQuantityException(),
                            type = LedgerEntryType.RETURN.name,
                            amountMinor = -toAccount.minorUnits,
                            invoiceId = creditNoteId,
                            method = null,
                            reference = null,
                            note = creditNote.note,
                            createdAt = creditNote.createdAtMillis,
                        ),
                    )
                }
                ReturnResult.Recorded(creditNoteId, number)
            }
        } catch (e: ReturnQuantityException) {
            ReturnResult.QuantityExceedsSold
        }
    }

    private fun localDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()
}

class RoomSettingsRepository(database: InvisionDatabase) : SettingsRepository {

    private val dao = database.billingDao()

    override fun observeSettings(): Flow<StoreSettings> = dao.observeSettings().map { it?.toDomain() ?: StoreSettings() }

    override suspend fun getSettings(): StoreSettings = dao.getSettings()?.toDomain() ?: StoreSettings()

    override suspend fun saveSettings(settings: StoreSettings) {
        dao.upsertSettings(settings.toEntity())
    }
}
