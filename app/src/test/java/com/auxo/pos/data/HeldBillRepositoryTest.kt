package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.entity.HeldBillEntity
import com.auxo.pos.data.repository.HeldCartCodec
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomHeldBillRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomItemDraft
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.DataIntegrity.count
import com.auxo.pos.testutil.TestData
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeldBillRepositoryTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var held: RoomHeldBillRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        held = RoomHeldBillRepository(database, TestData.clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun cartOfShirt(quantity: Int = 2): Cart {
        val fresh = TestData.tShirt().let { it.copy(id = 0, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        val id = (inventory.saveProduct(fresh) as SaveProductResult.Saved).productId
        val product = inventory.getProduct(id)!!
        return Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity)))
    }

    // The text form

    @Test
    fun codec_roundTripsEverythingTheBillNeeds() {
        val custom = CartLine.custom(CartLine.CUSTOM_KEY_BASE - 3, CustomItemDraft("Alteration", Money(150_00), 1, 1_800))
        val cart = Cart(
            lines = listOf(
                CartLine(
                    variantId = 11, productId = 1, productName = "Cotton T-Shirt", variantLabel = "M / Navy", barcode = "8901234567890",
                    hsnCode = "6109", unitPrice = Money(499_00), mrp = Money(599_00), gstRate = GstRate.Apparel, quantity = 3,
                    discount = Discount.Percent(1_250), discountReason = "Festival", availableStock = 5,
                ),
                custom.copy(discount = Discount.Amount(Money(10_00)), discountReason = "Goodwill"),
                TestData.dish(id = 4, price = Money(90_00)).let { CartLine.from(it, it.variants.single(), 2) },
            ),
            billDiscount = Discount.Amount(Money(25_00)),
            billDiscountReason = "Regular customer",
            customer = CustomerDetails(name = "Asha K", phone = "9876543210", gstin = "", customerId = 7),
            placeOfSupplyStateCode = "29",
        )
        val back = HeldCartCodec.decode(HeldCartCodec.encode(cart))!!
        // Stock figures are looked up again on resume; everything else must match.
        val expected = cart.copy(lines = cart.lines.map { if (it.isCustom) it else it.copy(availableStock = null) })
        assertEquals(expected.copy(lines = emptyList()), back.copy(lines = emptyList()))
        assertEquals(expected.lines.map { it.copy(availableStock = null) }, back.lines.map { it.copy(availableStock = null) })
        assertEquals(CartLine.UNLIMITED, back.lines[1].availableStock)
        assertNull(back.lines[2].availableStock)
        assertNull(back.lines[0].availableStock)
    }

    @Test
    fun codec_refusesDamagedOrNewerText() {
        assertNull(HeldCartCodec.decode(""))
        assertNull(HeldCartCodec.decode("{not json"))
        assertNull(HeldCartCodec.decode("""{"v":99,"lines":[],"customer":{}}"""))
        assertNull(HeldCartCodec.decode("""{"v":1,"lines":[{"key":1}],"customer":{}}"""))
    }

    // Keeping, resuming, discarding

    @Test
    fun aHeldBill_isKept_listedNewestFirst_andComesBackIntact() = runTest {
        val cart = cartOfShirt()
        val first = held.hold(cart, Money(998_00), "  Asha  ")
        TestData.clock.millis()
        val second = held.hold(cart.copy(billDiscount = Discount.Amount(Money(1_00)), billDiscountReason = "x"), Money(997_00), "Table 4")

        val list = held.observeHeld().first()
        assertEquals(setOf(first, second), list.map { it.id }.toSet())
        assertEquals("Asha", list.first { it.id == first }.label)
        assertEquals(2, list.first { it.id == first }.itemCount)
        assertEquals(Money(998_00), list.first { it.id == first }.total)

        val back = held.get(first)!!
        assertEquals(cart.lines.single().productName, back.cart.lines.single().productName)
        assertEquals(2, back.cart.lines.single().quantity)
    }

    @Test
    fun aHeldBill_survivesTheAppClosing() = runTest {
        val id = held.hold(cartOfShirt(), Money(998_00), "Asha")
        // A new repository on the same database, as after a restart.
        val reopened = RoomHeldBillRepository(database, TestData.clock)
        assertEquals("Asha", reopened.observeHeld().first().single().label)
        assertNotNull(reopened.get(id))
    }

    @Test
    fun holding_reservesNothing_andUsesNoNumber() = runTest {
        val cart = cartOfShirt(quantity = 8)
        val stockBefore = inventory.getProduct(cart.lines.single().productId)!!.variants[0].stockQuantity
        val tables = listOf("invoices", "payments", "stock_movements", "number_sequences", "customer_ledger", "audit_log")
        val before = tables.associateWith { count(database, it) }

        held.hold(cart, Money(3_992_00), "Big order")

        assertEquals(before, tables.associateWith { count(database, it) })
        assertEquals(stockBefore, inventory.getProduct(cart.lines.single().productId)!!.variants[0].stockQuantity)
        // The next sale still gets the first number.
        val billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        assertEquals("INV/26-27/00001", billing.previewInvoiceNumber("INV", java.time.LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun holdingAgain_updatesTheSameBill_andKeepsWhenItWasStarted() = runTest {
        val cart = cartOfShirt()
        val id = held.hold(cart, Money(998_00), "Asha")
        val created = held.observeHeld().first().single().createdAtMillis
        val again = held.hold(cart.copy(lines = cart.lines.map { it.copy(quantity = 3) }), Money(1_497_00), "Asha K", replacingId = id)
        assertEquals(id, again)
        val only = held.observeHeld().first().single()
        assertEquals("Asha K", only.label)
        assertEquals(3, only.itemCount)
        assertEquals(created, only.createdAtMillis)
        // If the old one is gone, a new one is made rather than failing.
        held.discard(id)
        val fresh = held.hold(cart, Money(998_00), "", replacingId = id)
        assertTrue(fresh != id)
    }

    @Test
    fun discard_removes_andIsHarmlessTwice() = runTest {
        val id = held.hold(cartOfShirt(), Money(998_00), "")
        held.discard(id)
        held.discard(id)
        assertTrue(held.observeHeld().first().isEmpty())
        assertNull(held.get(id))
    }

    @Test
    fun anEmptyBillCannotBeHeld() = runTest {
        try {
            held.hold(Cart(), Money.ZERO, "")
            fail("Expected an empty bill to be refused")
        } catch (e: IllegalArgumentException) {
            assertEquals(0, count(database, "held_bills"))
        }
    }

    @Test
    fun aDamagedBill_isStillListed_cannotBeResumed_butCanBeDiscarded() = runTest {
        val id = database.heldBillDao().insert(HeldBillEntity(label = "Old", createdAt = 1, updatedAt = 1, itemCount = 1, totalMinor = 100, payload = "garbage"))
        assertEquals("Old", held.observeHeld().first().single().label)
        assertNull(held.get(id))
        held.discard(id)
        assertEquals(0, count(database, "held_bills"))
    }
}
