package com.invision.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.OrderUpdateResult
import com.invision.pos.data.repository.ReceiveResult
import com.invision.pos.data.repository.ReturnResult
import com.invision.pos.data.repository.RoomBillingRepository
import com.invision.pos.data.repository.RoomCustomerRepository
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.RoomPurchasingRepository
import com.invision.pos.data.repository.SaleResult
import com.invision.pos.data.repository.SaveCustomerResult
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.CustomerDetails
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.Customer
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.PurchaseOrder
import com.invision.pos.domain.model.PurchaseOrderLine
import com.invision.pos.domain.model.PurchaseOrderStatus
import com.invision.pos.domain.model.PurchaseReceipt
import com.invision.pos.domain.model.PurchaseReceiptLine
import com.invision.pos.domain.model.Supplier
import com.invision.pos.testutil.DataIntegrity
import com.invision.pos.testutil.DataIntegrity.count
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/**
 * Money and stock stay in step, on the real Room database: failed saves leave nothing
 * behind, repeated taps save once, and the books reconcile after a busy day.
 */
@RunWith(AndroidJUnit4::class)
class BillingIntegrityTest {

    private lateinit var database: InvisionDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository
    private lateinit var customers: RoomCustomerRepository
    private lateinit var purchasing: RoomPurchasingRepository
    private val store = TestData.settings
    private val now = TestData.clock.millis()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InvisionDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        customers = RoomCustomerRepository(database, TestData.clock)
        purchasing = RoomPurchasingRepository(database, TestData.clock) { ZoneOffset.UTC }
    }

    @After
    fun tearDown() = database.close()

    private suspend fun saveNew(product: Product): Product {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        val id = (inventory.saveProduct(fresh) as SaveProductResult.Saved).productId
        return inventory.getProduct(id)!!
    }

    private suspend fun newCustomer(limit: Money? = null): Customer {
        val id = (customers.saveCustomer(Customer(name = "Anita Das", phone = "9830012345", creditLimit = limit)) as SaveCustomerResult.Saved).customerId
        return customers.getCustomer(id)!!
    }

    /** A balanced sale of [lines]; [onAccount] of it is put on [customer]'s account and the rest paid in cash. */
    private fun saleOf(lines: List<CartLine>, customer: Customer? = null, onAccount: (Money) -> Money = { Money.ZERO }): Invoice {
        val cart = Cart(
            lines = lines,
            customer = customer?.let { CustomerDetails(name = it.name, phone = it.phone.orEmpty(), customerId = it.id) } ?: CustomerDetails(),
        )
        val totals = BillCalculator.calculate(cart, store.taxModeFor(cart))
        val credit = onAccount(totals.grandTotal)
        val cash = totals.grandTotal - credit
        val payments = listOfNotNull(
            cash.takeIf { !it.isZero }?.let { PaymentRecord(method = PaymentMethod.CASH, amount = it) },
            credit.takeIf { !it.isZero }?.let { PaymentRecord(method = PaymentMethod.CREDIT, amount = it) },
        )
        return SaleBuilder.build(cart, totals, store, payments, now)
    }

    private suspend fun sold(invoice: Invoice, requestId: String? = null): SaleResult.Recorded =
        billing.recordSale(invoice, "INV", requestId) as SaleResult.Recorded

    private suspend fun returnNote(saleId: Long, quantities: (Invoice) -> Map<Long, Int>, method: PaymentMethod = PaymentMethod.CASH): Invoice {
        val original = billing.getInvoice(saleId)!!
        return (ReturnBuilder.build(original, quantities(original), method, "Test", now) as ReturnBuild.Ready).creditNote
    }

    private suspend fun stockOf(product: Product, index: Int = 0) = inventory.getProduct(product.id)!!.variants[index].stockQuantity

    /** Row counts of the tables a sale or return writes to. */
    private fun writes() = listOf("invoices", "invoice_lines", "payments", "stock_movements", "customer_ledger", "number_sequences", "applied_requests")
        .associateWith { count(database, it) }

    private fun nothingWritten() = writes().mapValues { 0 }

    // Rollback

    @Test
    fun saleWithAShortLine_savesNothing_andFreesTheRequestId() = runTest {
        val shirt = saveNew(TestData.tShirt()) // M: 8 in stock, L: 1
        val before = writes()
        val tooMany = saleOf(listOf(CartLine.from(shirt, shirt.variants[0], 2), CartLine.from(shirt, shirt.variants[1], 2)))

        val result = billing.recordSale(tooMany, "INV", "tap-1")

        assertEquals(SaleResult.StockShortage(setOf(shirt.variants[1].id)), result)
        assertEquals(before, writes()) // not even the number or the request id was used
        assertEquals(8, stockOf(shirt))
        val fixed = saleOf(listOf(CartLine.from(shirt, shirt.variants[0], 2), CartLine.from(shirt, shirt.variants[1], 1)))
        assertEquals("INV/26-27/00001", sold(fixed, "tap-1").number)
        DataIntegrity.assertConsistent(database)
    }

    @Test
    fun saleOverTheCreditLimit_savesNothing() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val customer = newCustomer(limit = Money(50_000))
        val before = writes()

        val result = billing.recordSale(saleOf(listOf(CartLine.from(shirt, shirt.variants[0], 2)), customer) { it }, "INV", "tap-1")

        assertEquals(SaleResult.CreditLimitExceeded, result)
        assertEquals(before, writes())
        assertEquals(8, stockOf(shirt))
        DataIntegrity.assertConsistent(database)
    }

    @Test
    fun invoiceThatDoesNotBalance_isRefused_andNothingIsSaved() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val before = writes()
        val good = saleOf(listOf(CartLine.from(shirt, shirt.variants[0], 1)))
        val broken = mapOf(
            "payments short by ₹1" to good.copy(payments = good.payments.map { it.copy(amount = it.amount - Money(100)) }),
            "grand total changed" to good.copy(grandTotal = good.grandTotal + Money(100)),
            "line total changed" to good.copy(lines = good.lines.map { it.copy(total = it.total + Money(1)) }),
            "tax totals changed" to good.copy(cgstTotal = good.cgstTotal + Money(1)),
            "zero quantity" to good.copy(lines = good.lines.map { it.copy(quantity = 0) }),
            "refund on a sale" to good.copy(payments = good.payments.map { it.copy(kind = com.invision.pos.domain.model.PaymentKind.REFUND) }),
            "no payments" to good.copy(payments = emptyList()),
        )
        for ((why, invoice) in broken) {
            try {
                billing.recordSale(invoice, "INV", "tap-1")
                fail("A sale with $why was saved")
            } catch (expected: IllegalArgumentException) {
                // refused before anything was written
            }
        }
        assertEquals(before, writes())
        assertEquals(8, stockOf(shirt))
        assertEquals("INV/26-27/00001", sold(good, "tap-1").number)
        DataIntegrity.assertConsistent(database)
    }

    @Test
    fun returnForAnotherSalesLine_isRefused_andNothingChanges() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val first = sold(saleOf(listOf(CartLine.from(shirt, shirt.variants[0], 2))))
        val second = sold(saleOf(listOf(CartLine.from(shirt, shirt.variants[0], 2))))
        val otherLine = billing.getInvoice(second.invoiceId)!!.lines.single().id
        val note = returnNote(first.invoiceId, { sale -> mapOf(sale.lines.single().id to 1) })
        val before = writes()

        val result = billing.recordReturn(note.copy(lines = note.lines.map { it.copy(originalLineId = otherLine) }), "CN")

        assertEquals(ReturnResult.QuantityExceedsSold, result)
        assertEquals(before, writes())
        assertEquals(0, billing.getInvoice(second.invoiceId)!!.lines.single().returnedQuantity)
        assertEquals(0, billing.getInvoice(first.invoiceId)!!.lines.single().returnedQuantity)
        DataIntegrity.assertConsistent(database)
    }

    @Test
    fun returnWithOneBadLine_putsBackNothing() = runTest {
        val shirt = saveNew(TestData.tShirt()) // M: 8, L: 1
        val sale = sold(saleOf(listOf(CartLine.from(shirt, shirt.variants[0], 2), CartLine.from(shirt, shirt.variants[1], 1))))
        val note = returnNote(sale.invoiceId, { s -> s.lines.associate { it.id to 1 } })
        val before = writes()

        // The second line asks for three units back although one was sold.
        val bad = note.copy(lines = note.lines.mapIndexed { i, line -> if (i == 1) line.copy(quantity = 3) else line })
        assertEquals(ReturnResult.QuantityExceedsSold, billing.recordReturn(bad, "CN"))

        assertEquals(before, writes())
        assertTrue(billing.getInvoice(sale.invoiceId)!!.lines.all { it.returnedQuantity == 0 })
        assertEquals(6, stockOf(shirt, 0))
        assertEquals("CN/26-27/00001", (billing.recordReturn(note, "CN") as ReturnResult.Recorded).number)
        DataIntegrity.assertConsistent(database)
    }

    @Test
    fun receiptThatOverReceivesOneLine_receivesNothing() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val biscuits = saveNew(TestData.biscuits())
        val supplierId = purchasing.saveSupplier(Supplier(name = "Fresh Mills"))
        fun orderLine(p: Product, qty: Int) = PurchaseOrderLine(
            variantId = p.variants[0].id, productName = p.name, variantLabel = p.variants[0].attributesLabel,
            stockUnit = p.stockUnit, quantityOrdered = qty, unitCost = Money(2_000),
        )
        val saved = purchasing.saveDraftOrder(PurchaseOrder(supplierId = supplierId, lines = listOf(orderLine(shirt, 4), orderLine(biscuits, 2)))) as OrderUpdateResult.Saved
        assertTrue(purchasing.setOrderStatus(saved.orderId, PurchaseOrderStatus.ORDERED))
        val order = purchasing.getOrder(saved.orderId)!!
        fun receiptLine(p: Product, qty: Int, orderLineId: Long) = PurchaseReceiptLine(
            variantId = p.variants[0].id, orderLineId = orderLineId, productName = p.name, variantLabel = p.variants[0].attributesLabel,
            stockUnit = p.stockUnit, quantity = qty, unitCost = Money(2_000),
        )
        val shirtStock = stockOf(shirt)
        val biscuitStock = stockOf(biscuits)
        val before = writes()

        val result = purchasing.receive(
            PurchaseReceipt(orderId = order.id, lines = listOf(receiptLine(shirt, 4, order.lines[0].id), receiptLine(biscuits, 5, order.lines[1].id))),
            "recv-1",
        )

        assertEquals(ReceiveResult.OverReceived(setOf(order.lines[1].id)), result)
        assertEquals(before, writes())
        assertEquals(0, count(database, "purchase_receipts"))
        assertEquals(0, count(database, "purchase_receipt_lines"))
        assertEquals(shirtStock, stockOf(shirt))
        assertEquals(biscuitStock, stockOf(biscuits))
        assertEquals(PurchaseOrderStatus.ORDERED, purchasing.getOrder(order.id)!!.status)
        assertTrue(purchasing.getOrder(order.id)!!.lines.all { it.quantityReceived == 0 })
        // The same request id works once the receipt is right.
        val ok = purchasing.receive(
            PurchaseReceipt(orderId = order.id, lines = listOf(receiptLine(shirt, 4, order.lines[0].id), receiptLine(biscuits, 2, order.lines[1].id))),
            "recv-1",
        )
        assertTrue(ok is ReceiveResult.Recorded)
        assertEquals(PurchaseOrderStatus.RECEIVED, purchasing.getOrder(order.id)!!.status)
        DataIntegrity.assertConsistent(database)
    }

    // Duplicates and limits on returns

    @Test
    fun theSameReturnRequest_returnsOnce() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val sale = sold(saleOf(listOf(CartLine.from(shirt, shirt.variants[0], 3))))
        fun oneUnit(s: Invoice) = mapOf(s.lines.single().id to 1)

        val first = billing.recordReturn(returnNote(sale.invoiceId, ::oneUnit), "CN", "ret-1") as ReturnResult.Recorded
        val again = billing.recordReturn(returnNote(sale.invoiceId, ::oneUnit), "CN", "ret-1") as ReturnResult.Recorded

        assertEquals(first, again)
        assertEquals(1, billing.getInvoice(sale.invoiceId)!!.lines.single().returnedQuantity)
        assertEquals(6, stockOf(shirt)) // 8 - 3 sold + 1 back
        assertEquals(2, count(database, "invoices")) // the sale and one credit note
        val next = billing.recordReturn(returnNote(sale.invoiceId, ::oneUnit), "CN", "ret-2") as ReturnResult.Recorded
        assertEquals("CN/26-27/00002", next.number)
        assertEquals(2, billing.getInvoice(sale.invoiceId)!!.lines.single().returnedQuantity)
        DataIntegrity.assertConsistent(database)
    }

    @Test
    fun refundsTogether_cannotPassTheSalesTotal() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val sale = sold(saleOf(listOf(CartLine.from(shirt, shirt.variants[0], 2)))) // ₹998
        val oneUnit = { s: Invoice -> mapOf(s.lines.single().id to 1) }
        assertTrue(billing.recordReturn(returnNote(sale.invoiceId, oneUnit), "CN") is ReturnResult.Recorded)

        // A credit note that balances on its own but would refund ₹100 more than is left of the sale.
        val honest = returnNote(sale.invoiceId, oneUnit)
        val extra = Money(10_000)
        val inflated = honest.copy(
            lines = honest.lines.map { it.copy(taxable = it.taxable + extra, total = it.total + extra) },
            taxableTotal = honest.taxableTotal + extra,
            grandTotal = honest.grandTotal + extra,
            payments = honest.payments.map { it.copy(amount = it.amount + extra) },
        )
        val before = writes()

        assertEquals(ReturnResult.QuantityExceedsSold, billing.recordReturn(inflated, "CN"))
        assertEquals(before, writes())
        assertTrue(billing.recordReturn(honest, "CN") is ReturnResult.Recorded)
        DataIntegrity.assertConsistent(database)
    }

    // Reconciliation after a mixed day

    @Test
    fun aBusyDay_reconcilesStockPaymentsReturnsAndTheLedger() = runTest {
        val shirt = saveNew(TestData.tShirt())
        val biscuits = saveNew(TestData.biscuits())
        val customer = newCustomer()
        val supplierId = purchasing.saveSupplier(Supplier(name = "Fresh Mills"))
        val shirtM = shirt.variants[0]
        val biscuit = biscuits.variants[0]

        // Half of a mixed bill goes on account.
        val a = sold(saleOf(listOf(CartLine.from(shirt, shirtM, 2), CartLine.from(biscuits, biscuit, 3)), customer) { Money(it.minorUnits / 2) })
        val b = sold(saleOf(listOf(CartLine.from(biscuits, biscuit, 5))))
        DataIntegrity.assertConsistent(database)

        // One shirt goes back to the account, one packet back for cash.
        val lineOf = { sale: Invoice, name: String -> sale.lines.first { it.productName == name }.id }
        assertTrue(
            billing.recordReturn(returnNote(a.invoiceId, { s -> mapOf(lineOf(s, shirt.name) to 1) }, PaymentMethod.CREDIT), "CN", "r-1") is ReturnResult.Recorded,
        )
        assertTrue(
            billing.recordReturn(returnNote(a.invoiceId, { s -> mapOf(lineOf(s, biscuits.name) to 1) }), "CN", "r-2") is ReturnResult.Recorded,
        )

        // Stock arrives in a new batch, the customer pays part of what they owe, and more is sold and returned.
        val received = purchasing.receive(
            PurchaseReceipt(
                supplierId = supplierId,
                lines = listOf(
                    PurchaseReceiptLine(
                        variantId = biscuit.id, productName = biscuits.name, variantLabel = biscuit.attributesLabel, stockUnit = biscuits.stockUnit,
                        quantity = 10, unitCost = Money(2_000), batchNumber = "B-NEW", expiryDate = TestData.today.plusDays(120),
                    ),
                ),
            ),
            "recv-1",
        )
        assertTrue(received is ReceiveResult.Recorded)
        assertTrue(customers.receivePayment(customer.id, Money(2_000), PaymentMethod.CASH, null, null, "pay-1") is com.invision.pos.data.repository.AccountPaymentResult.Recorded)
        val c = sold(saleOf(listOf(CartLine.from(biscuits, biscuit, 4))))
        assertTrue(billing.recordReturn(returnNote(b.invoiceId, { s -> mapOf(s.lines.single().id to 2) }), "CN", "r-3") is ReturnResult.Recorded)
        assertTrue(billing.recordReturn(returnNote(c.invoiceId, { s -> mapOf(s.lines.single().id to 4) }), "CN", "r-4") is ReturnResult.Recorded)

        DataIntegrity.assertConsistent(database)
        // 3 sold twice over to the same bill, minus what came back: spot-check the arithmetic too.
        assertEquals(biscuitsStartStock - 3 - 5 + 1 + 10 - 4 + 2 + 4, stockOf(biscuits))
        assertEquals(8 - 2 + 1, stockOf(shirt))
    }

    private val biscuitsStartStock = TestData.biscuits().variants[0].stockQuantity
}
