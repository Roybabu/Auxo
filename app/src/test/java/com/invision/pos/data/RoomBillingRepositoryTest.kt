package com.invision.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.ReturnResult
import com.invision.pos.data.repository.RoomBillingRepository
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.RoomSettingsRepository
import com.invision.pos.data.repository.SaleResult
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.PaymentDraft
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.InvoiceType
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentKind
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.ReceiptPaper
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StoreSettings
import com.invision.pos.domain.usecase.CheckoutResult
import com.invision.pos.domain.usecase.CheckoutUseCase
import com.invision.pos.domain.usecase.ProcessReturnResult
import com.invision.pos.domain.usecase.ProcessReturnUseCase
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/** Sales, returns and settings against the real Room database (in memory). */
@RunWith(AndroidJUnit4::class)
class RoomBillingRepositoryTest {

    private lateinit var database: InvisionDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository
    private lateinit var settings: RoomSettingsRepository
    private val store = TestData.settings

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InvisionDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        settings = RoomSettingsRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun saveNew(product: Product): Product {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        val id = (inventory.saveProduct(fresh) as SaveProductResult.Saved).productId
        return inventory.getProduct(id)!!
    }

    private fun sale(vararg lines: CartLine, at: Long = TestData.clock.millis()): Invoice {
        val cart = Cart(lines = lines.toList())
        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        val payments = listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal))
        return SaleBuilder.build(cart, totals, store, payments, at)
    }

    private suspend fun stockOf(product: Product, index: Int = 0) =
        inventory.getProduct(product.id)!!.variants[index].stockQuantity

    @Test
    fun sale_savesInvoiceLinesPaymentsAndDeductsStock() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val result = billing.recordSale(sale(CartLine.from(shirt, shirt.variants[0], quantity = 3)), "INV")
        result as SaleResult.Recorded
        assertEquals("INV/26-27/00001", result.number)

        val invoice = billing.getInvoice(result.invoiceId)!!
        assertEquals(3, invoice.lines.single().quantity)
        assertEquals(invoice.grandTotal, invoice.payments.single().amount)
        assertEquals(5, stockOf(shirt))
        val movements = inventory.observeStockMovements(shirt.id).first()
        assertEquals(StockChangeReason.SOLD, movements.first().reason)
        assertEquals(-3, movements.first().quantityChange)
        assertEquals("INV/26-27/00001", movements.first().note)
    }

    @Test
    fun shortStock_savesNothing() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val biscuits = saveNew(TestData.biscuits())
        val result = billing.recordSale(
            sale(CartLine.from(biscuits, biscuits.variants[0], quantity = 2), CartLine.from(shirt, shirt.variants[1], quantity = 2)),
            "INV",
        )
        assertEquals(SaleResult.StockShortage(setOf(shirt.variants[1].id)), result)
        assertEquals(40, stockOf(biscuits)) // the other line was not deducted either
        assertTrue(billing.observeInvoices(0, Long.MAX_VALUE).first().isEmpty())

        // The number was not used up.
        val next = billing.recordSale(sale(CartLine.from(biscuits, biscuits.variants[0])), "INV") as SaleResult.Recorded
        assertEquals("INV/26-27/00001", next.number)
    }

    @Test
    fun numbering_isPerPrefixAndFinancialYear() = runTest {
        // No expiry, so the sales next April can still take this stock.
        val biscuits = saveNew(TestData.biscuits(expiry = null))
        val line = CartLine.from(biscuits, biscuits.variants[0])
        val march = java.time.LocalDate.of(2027, 3, 31).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val april = java.time.LocalDate.of(2027, 4, 1).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("INV/26-27/00001", (billing.recordSale(sale(line), "INV") as SaleResult.Recorded).number)
        assertEquals("INV/26-27/00002", (billing.recordSale(sale(line, at = march), "INV") as SaleResult.Recorded).number)
        assertEquals("INV/27-28/00001", (billing.recordSale(sale(line, at = april), "INV") as SaleResult.Recorded).number)
        assertEquals("B/26-27/00001", (billing.recordSale(sale(line), "B") as SaleResult.Recorded).number)
    }

    @Test
    fun numbering_followsIndianTimeAtTheFinancialYearBoundary() = runTest {
        val biscuits = saveNew(TestData.biscuits(expiry = null))
        val line = CartLine.from(biscuits, biscuits.variants[0])
        val ist = billingInBusinessZone()
        fun utc(day: Int, hour: Int, minute: Int) =
            java.time.LocalDate.of(2027, 3, day).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

        // 18:29 UTC on 31 March is 23:59 in India, still the old financial year.
        assertEquals("INV/26-27/00001", (ist.recordSale(sale(line, at = utc(31, 18, 29)), "INV") as SaleResult.Recorded).number)
        // 18:30 UTC is midnight in India, the first minute of 1 April.
        assertEquals("INV/27-28/00001", (ist.recordSale(sale(line, at = utc(31, 18, 30)), "INV") as SaleResult.Recorded).number)
    }

    @Test
    fun numbering_ignoresTheDevicesOwnTimeZone() = runTest {
        val biscuits = saveNew(TestData.biscuits(expiry = null))
        val line = CartLine.from(biscuits, biscuits.variants[0])
        val ist = billingInBusinessZone()
        // 20:00 UTC on 31 March is 01:30 on 1 April in India, but still 31 March in New York.
        val instant = java.time.LocalDate.of(2027, 3, 31).atTime(20, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            assertEquals("INV/27-28/00001", (ist.recordSale(sale(line, at = instant), "INV") as SaleResult.Recorded).number)
        } finally {
            java.util.TimeZone.setDefault(original)
        }
    }

    /** A repository built the way the app builds it, with no zone override. */
    private fun billingInBusinessZone() = RoomBillingRepository(database)

    @Test
    fun return_restocksMarksLinesAndRecordsRefund() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val saleId = (billing.recordSale(sale(CartLine.from(shirt, shirt.variants[0], quantity = 3)), "INV") as SaleResult.Recorded).invoiceId
        val original = billing.getInvoice(saleId)!!
        val lineId = original.lines.single().id

        val note = (ReturnBuilder.build(original, mapOf(lineId to 2), PaymentMethod.UPI, "Damaged", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
        val result = billing.recordReturn(note, "CN") as ReturnResult.Recorded
        assertEquals("CN/26-27/00001", result.number)

        assertEquals(7, stockOf(shirt))
        assertEquals(2, billing.getInvoice(saleId)!!.lines.single().returnedQuantity)
        val creditNote = billing.getInvoice(result.creditNoteId)!!
        assertEquals(InvoiceType.RETURN, creditNote.type)
        assertEquals(PaymentKind.REFUND, creditNote.payments.single().kind)
        assertEquals(PaymentMethod.UPI, creditNote.payments.single().method)
        assertEquals(listOf(result.creditNoteId), billing.observeCreditNotesFor(saleId).first().map { it.id })

        // Returning more than is left is refused, and nothing changes.
        val tooMany = note.copy(lines = note.lines.map { it.copy(quantity = 2) })
        assertEquals(ReturnResult.QuantityExceedsSold, billing.recordReturn(tooMany, "CN"))
        assertEquals(7, stockOf(shirt))
        assertEquals(2, billing.getInvoice(saleId)!!.lines.single().returnedQuantity)
    }

    @Test
    fun deletingAProduct_keepsItsInvoices() = runTest {
        val juice = saveNew(TestData.juice(stock = 5))
        val saleId = (billing.recordSale(sale(CartLine.from(juice, juice.variants[0])), "INV") as SaleResult.Recorded).invoiceId
        inventory.deleteProduct(juice.id)

        val invoice = billing.getInvoice(saleId)!!
        assertEquals("Mango Juice", invoice.lines.single().productName)
        assertNull(invoice.lines.single().variantId)

        // The return still works; there is just no stock to put back.
        val note = (ReturnBuilder.build(invoice, mapOf(invoice.lines.single().id to 1), PaymentMethod.CASH, "", 1) as ReturnBuild.Ready).creditNote
        assertTrue(billing.recordReturn(note, "CN") is ReturnResult.Recorded)
    }

    @Test
    fun salesTotalsAndPayments_forAPeriod() = runTest {
        val biscuits = saveNew(TestData.biscuits())
        val saleId = (billing.recordSale(sale(CartLine.from(biscuits, biscuits.variants[0], quantity = 10)), "INV") as SaleResult.Recorded).invoiceId
        billing.recordSale(sale(CartLine.from(biscuits, biscuits.variants[0], quantity = 1)), "INV")
        val original = billing.getInvoice(saleId)!!
        val note = (ReturnBuilder.build(original, mapOf(original.lines.single().id to 2), PaymentMethod.CASH, "", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
        billing.recordReturn(note, "CN")

        val totals = billing.observeSalesTotals(0, Long.MAX_VALUE).first()
        assertEquals(2, totals.billCount)
        assertEquals(Money(300_00 + 30_00 - 60_00), totals.net)
        val payments = billing.observePayments(0, Long.MAX_VALUE).first()
        assertEquals(3, payments.size)
        assertEquals(1, payments.count { it.kind == PaymentKind.REFUND })
        assertTrue(payments.all { it.invoiceNumber != null })
        assertEquals(3, billing.observeInvoices(0, Long.MAX_VALUE).first().size)
        assertTrue(billing.observeInvoices(0, 1).first().isEmpty())
    }

    @Test
    fun settings_defaultUntilSaved_thenRoundTrip() = runTest {
        assertEquals(StoreSettings(), settings.getSettings())
        val custom = store.copy(gstRegistered = true, pricesIncludeTax = false, receipt = store.receipt.copy(paper = ReceiptPaper.A5, showSavings = false))
        settings.saveSettings(custom)
        assertEquals(custom, settings.getSettings())
        assertEquals(custom, settings.observeSettings().first())
    }

    @Test
    fun checkoutAndReturnUseCases_endToEnd() = runTest {
        settings.saveSettings(store)
        val shirt = saveNew(TestData.tShirt())
        val checkout = CheckoutUseCase(billing, settings, TestData.clock)
        val cart = Cart().add(CartLine.from(shirt, shirt.variants[0], quantity = 2))
        val total = BillCalculator.calculate(cart, store.taxModeFor(cart)).grandTotal

        val result = checkout(
            cart,
            listOf(
                PaymentDraft(1, PaymentMethod.CASH, Money(500_00).toDecimalString(), tendered = "500"),
                PaymentDraft(2, PaymentMethod.UPI, (total - Money(500_00)).toDecimalString()),
            ),
        ) as CheckoutResult.Completed
        assertEquals(6, stockOf(shirt))
        assertEquals(2, billing.getInvoice(result.invoiceId)!!.payments.size)

        val returned = ProcessReturnUseCase(billing, settings, TestData.clock)(
            result.invoiceId,
            mapOf(billing.getInvoice(result.invoiceId)!!.lines.single().id to 2),
            PaymentMethod.CASH,
            "Changed mind",
        ) as ProcessReturnResult.Completed
        assertEquals(total, returned.refund)
        assertEquals(8, stockOf(shirt))
    }

    @Test
    fun partialReturnsOfARoundedBill_refundExactlyWhatWasPaid() = runTest {
        settings.saveSettings(store)
        // 3 x 99.50 = 298.50, billed as 299.00 after round-off.
        val biscuits = TestData.biscuits().let { product ->
            saveNew(product.copy(variants = product.variants.map { it.copy(sellingPrice = Money(99_50), mrp = null) }))
        }
        val cart = Cart().add(CartLine.from(biscuits, biscuits.variants[0], quantity = 3))
        val sale = CheckoutUseCase(billing, settings, TestData.clock)(cart, listOf(PaymentDraft(1, PaymentMethod.CASH, "299"))) as CheckoutResult.Completed
        val lineId = billing.getInvoice(sale.invoiceId)!!.lines.single().id

        val processReturn = ProcessReturnUseCase(billing, settings, TestData.clock)
        val refunds = List(3) {
            (processReturn(sale.invoiceId, mapOf(lineId to 1), PaymentMethod.CASH, "") as ProcessReturnResult.Completed).refund
        }
        assertEquals(listOf(Money(100_00), Money(99_00), Money(100_00)), refunds)

        val payments = billing.observePayments(0, Long.MAX_VALUE).first()
        val paid = payments.filter { it.kind == PaymentKind.PAYMENT }.fold(Money.ZERO) { acc, p -> acc + p.amount }
        val refunded = payments.filter { it.kind == PaymentKind.REFUND }.fold(Money.ZERO) { acc, p -> acc + p.amount }
        assertEquals(paid, refunded)
        assertEquals(Money.ZERO, billing.observeSalesTotals(0, Long.MAX_VALUE).first().net)
        assertEquals(40, stockOf(biscuits))
        assertTrue(billing.getInvoice(sale.invoiceId)!!.isFullyReturned)
    }

    @Test
    fun checkout_requiresSetupForRegisteredStoreWithoutGstin() = runTest {
        settings.saveSettings(StoreSettings(gstRegistered = true, stateCode = "19"))
        val biscuits = saveNew(TestData.biscuits())
        val result = CheckoutUseCase(billing, settings, TestData.clock)(
            Cart().add(CartLine.from(biscuits, biscuits.variants[0])),
            listOf(PaymentDraft(1, PaymentMethod.CASH, "30")),
        )
        assertEquals(CheckoutResult.SetupRequired, result)
        assertEquals(40, stockOf(biscuits))
    }
}
