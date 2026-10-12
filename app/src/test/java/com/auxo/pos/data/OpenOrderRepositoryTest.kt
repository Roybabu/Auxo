package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.KitchenOrderCodec
import com.auxo.pos.data.repository.HeldCartCodec
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomHeldBillRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.KotLine
import com.auxo.pos.domain.billing.PlaceOrderResult
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.SentItem
import com.auxo.pos.domain.billing.ServiceType
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.testutil.DataIntegrity.count
import com.auxo.pos.testutil.TestData
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenOrderRepositoryTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var orders: RoomHeldBillRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        orders = RoomHeldBillRepository(database, TestData.clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun shirt(): Product {
        val fresh = TestData.tShirt().let { it.copy(id = 0, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        val id = (inventory.saveProduct(fresh) as SaveProductResult.Saved).productId
        return inventory.getProduct(id)!!
    }

    private fun cart(product: Product, qty: Int, service: ServiceType? = ServiceType.DINE_IN, table: String = "4") =
        Cart(lines = listOf(CartLine.from(product, product.variants[0], qty)), serviceType = service, tableNumber = table)

    private fun placed(result: PlaceOrderResult) = result as PlaceOrderResult.Placed

    @Test
    fun firstOrder_makesAnOpenTableAndTheFirstTicket() = runTest {
        val p = shirt()
        val result = placed(orders.placeOrder(cart(p, 2), Money(998_00), "Asha"))
        assertEquals("KOT-001", result.kotNumber)
        assertFalse(result.isAddOn)
        assertEquals(listOf(KotLine("Cotton T-Shirt", p.variants[0].attributesLabel, 2)), result.delta.added)

        val open = orders.observeHeld().first().single()
        assertTrue(open.isOpenOrder)
        assertEquals(ServiceType.DINE_IN, open.serviceType)
        assertEquals("4", open.tableNumber)
        assertEquals(1, open.kotCount)
        assertEquals("Asha", open.label)
    }

    @Test
    fun addingItemsToTheOpenTable_sendsOnlyTheExtras() = runTest {
        val p = shirt()
        val first = placed(orders.placeOrder(cart(p, 2), Money(998_00), ""))
        val again = placed(orders.placeOrder(cart(p, 3), Money(1_497_00), "", replacingId = first.orderId))
        assertEquals(first.orderId, again.orderId)
        assertEquals("KOT-002", again.kotNumber)
        assertTrue(again.isAddOn)
        assertEquals(1, again.delta.added.single().quantity)
        assertEquals(2, again.kotCount)
        assertEquals(1, orders.observeHeld().first().size)
        assertEquals(3, orders.get(first.orderId)!!.cart.lines.single().quantity)
    }

    @Test
    fun aTableCannotHaveTwoOpenOrders() = runTest {
        val p = shirt()
        val first = placed(orders.placeOrder(cart(p, 1, table = "t4"), Money(499_00), ""))
        val before = count(database, "held_bills")
        val result = orders.placeOrder(cart(p, 1, table = " T4 "), Money(499_00), "")
        assertEquals(PlaceOrderResult.TableInUse(first.orderId), result)
        assertEquals(before, count(database, "held_bills"))
        // A different table is fine, and so is the same table once the first is settled or discarded.
        placed(orders.placeOrder(cart(p, 1, table = "5"), Money(499_00), ""))
        orders.discard(first.orderId)
        placed(orders.placeOrder(cart(p, 1, table = "4"), Money(499_00), ""))
    }

    @Test
    fun takeawayOrders_needNoTable_andCanBeManyAtOnce() = runTest {
        val p = shirt()
        placed(orders.placeOrder(cart(p, 1, ServiceType.TAKEAWAY, ""), Money(499_00), "Ravi"))
        placed(orders.placeOrder(cart(p, 1, ServiceType.TAKEAWAY, ""), Money(499_00), "Sita"))
        val list = orders.observeHeld().first()
        assertEquals(2, list.size)
        assertTrue(list.all { it.serviceType == ServiceType.TAKEAWAY && it.tableNumber == null })
    }

    @Test
    fun nothingNew_savesNothing_andUsesNoTicketNumber() = runTest {
        val p = shirt()
        val c = cart(p, 2)
        val first = placed(orders.placeOrder(c, Money(998_00), ""))
        assertEquals(PlaceOrderResult.NothingNew, orders.placeOrder(c, Money(998_00), "", replacingId = first.orderId))
        val next = placed(orders.placeOrder(cart(p, 3), Money(1_497_00), "", replacingId = first.orderId))
        assertEquals("KOT-002", next.kotNumber)
    }

    @Test
    fun cancelledItems_areOnTheNextTicket() = runTest {
        val p = shirt()
        val first = placed(orders.placeOrder(cart(p, 3), Money(1_497_00), ""))
        val smaller = placed(orders.placeOrder(cart(p, 2), Money(998_00), "", replacingId = first.orderId))
        assertTrue(smaller.delta.added.isEmpty())
        assertEquals(1, smaller.delta.cancelled.single().quantity)
    }

    @Test
    fun ticketNumbers_startAgainEachDay() = runTest {
        val p = shirt()
        val today = RoomHeldBillRepository(database, TestData.clock)
        val tomorrow = RoomHeldBillRepository(database, Clock.fixed(Instant.ofEpochMilli(TestData.clock.millis() + 24 * 3_600_000L), ZoneOffset.UTC))
        assertEquals("KOT-001", placed(today.placeOrder(cart(p, 1, table = "1"), Money(1), "")).kotNumber)
        assertEquals("KOT-002", placed(today.placeOrder(cart(p, 1, table = "2"), Money(1), "")).kotNumber)
        assertEquals("KOT-001", placed(tomorrow.placeOrder(cart(p, 1, table = "3"), Money(1), "")).kotNumber)
    }

    @Test
    fun anOrder_needsAServiceType_andDineInNeedsATable() = runTest {
        val p = shirt()
        for (bad in listOf(cart(p, 1, service = null), cart(p, 1, ServiceType.DINE_IN, "  "))) {
            try {
                orders.placeOrder(bad, Money(1), "")
                fail("Expected the order to be refused")
            } catch (e: IllegalArgumentException) {
                assertEquals(0, count(database, "held_bills"))
            } catch (e: IllegalStateException) {
                assertEquals(0, count(database, "held_bills"))
            }
        }
    }

    @Test
    fun placingAnOrder_changesNoStock_noInvoiceNumber_noLedger() = runTest {
        val p = shirt()
        val tables = listOf("invoices", "payments", "stock_movements", "customer_ledger", "audit_log")
        val before = tables.associateWith { count(database, it) }
        val stock = inventory.getProduct(p.id)!!.variants[0].stockQuantity
        placed(orders.placeOrder(cart(p, 4), Money(1_996_00), ""))
        assertEquals(before, tables.associateWith { count(database, it) })
        assertEquals(stock, inventory.getProduct(p.id)!!.variants[0].stockQuantity)
        val billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        assertEquals("INV/26-27/00001", billing.previewInvoiceNumber("INV", java.time.LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun settlingTheTable_isANormalInvoice_withTheTable_andNormalStockRules() = runTest {
        val p = shirt()
        val c = cart(p, 2, table = "4")
        val open = placed(orders.placeOrder(c, Money(998_00), "Asha"))
        val resumed = orders.get(open.orderId)!!.cart
        assertEquals(ServiceType.DINE_IN, resumed.serviceType)
        assertEquals("4", resumed.tableNumber)

        val store = TestData.settings
        val totals = BillCalculator.calculate(resumed, store.taxModeFor(resumed))
        val invoice = SaleBuilder.build(resumed, totals, store, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), TestData.clock.millis())
        val billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        val before = inventory.getProduct(p.id)!!.variants[0].stockQuantity
        val sold = billing.recordSale(invoice, "INV") as SaleResult.Recorded
        orders.discard(open.orderId)

        val saved = billing.getInvoice(sold.invoiceId)!!
        assertEquals("INV/26-27/00001", saved.number)
        assertEquals(ServiceType.DINE_IN, saved.serviceType)
        assertEquals("4", saved.tableNumber)
        assertEquals(before - 2, inventory.getProduct(p.id)!!.variants[0].stockQuantity)
        assertNull(orders.get(open.orderId))
    }

    @Test
    fun aTakeawayInvoice_hasNoTable() = runTest {
        val p = shirt()
        val c = cart(p, 1, ServiceType.TAKEAWAY, "7")
        val store = TestData.settings
        val totals = BillCalculator.calculate(c, store.taxModeFor(c))
        val invoice = SaleBuilder.build(c, totals, store, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), TestData.clock.millis())
        val sold = RoomBillingRepository(database) { ZoneOffset.UTC }.recordSale(invoice, "INV") as SaleResult.Recorded
        val saved = RoomBillingRepository(database) { ZoneOffset.UTC }.getInvoice(sold.invoiceId)!!
        assertEquals(ServiceType.TAKEAWAY, saved.serviceType)
        assertNull(saved.tableNumber)
    }

    @Test
    fun theCodecs_roundTripTheServiceAndWhatTheKitchenKnows() {
        val c = Cart(serviceType = ServiceType.DINE_IN, tableNumber = "A2")
        val back = HeldCartCodec.decode(HeldCartCodec.encode(c))!!
        assertEquals(ServiceType.DINE_IN, back.serviceType)
        assertEquals("A2", back.tableNumber)
        assertNull(HeldCartCodec.decode(HeldCartCodec.encode(Cart()))!!.serviceType)

        val items = listOf(SentItem(-1, "Dosa", "Breakfast", 2), SentItem(-2, "Tea", "", 1))
        assertEquals(items, KitchenOrderCodec.decode(KitchenOrderCodec.encode(items)))
        assertTrue(KitchenOrderCodec.decode("garbage").isEmpty())
        assertTrue(KitchenOrderCodec.decode(null).isEmpty())
    }
}
