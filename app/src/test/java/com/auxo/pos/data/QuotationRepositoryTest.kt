package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.RoomQuotationRepository
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.CustomerDetails
import com.auxo.pos.domain.billing.Discount
import com.auxo.pos.domain.billing.QuotationStatus
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.DataIntegrity.count
import com.auxo.pos.testutil.TestData
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuotationRepositoryTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var quotes: RoomQuotationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        quotes = RoomQuotationRepository(database, TestData.clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun cartOfShirt(quantity: Int = 2): Cart {
        val fresh = TestData.tShirt().let { it.copy(id = 0, variants = it.variants.map { v -> v.copy(id = 0, productId = 0) }) }
        val id = (inventory.saveProduct(fresh) as SaveProductResult.Saved).productId
        val product = inventory.getProduct(id)!!
        return Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity)))
    }

    private val until = LocalDate.of(2026, 10, 25)

    @Test
    fun saving_givesTheNextQuotationNumber_inItsOwnSeries() = runTest {
        val cart = cartOfShirt()
        val first = quotes.save(cart, Money(998_00), until)
        val second = quotes.save(cart, Money(998_00), until)
        assertEquals("QT/26-27/00001", first.number)
        assertEquals("QT/26-27/00002", second.number)
        assertEquals(until, first.validUntil)
        assertEquals(QuotationStatus.OPEN, first.status)
        assertNull(first.convertedInvoiceId)
    }

    @Test
    fun savingAQuotation_changesNoStock_noInvoice_noInvoiceNumber_noAccount() = runTest {
        val cart = cartOfShirt()
        val stock = database.openHelper.readableDatabase.query("SELECT SUM(stock_quantity) FROM product_variants").use { it.moveToFirst(); it.getInt(0) }
        val movements = count(database, "stock_movements")
        quotes.save(cart.copy(customer = CustomerDetails(name = "Asha", phone = "9876543210")), Money(998_00), until)
        quotes.save(cart, Money(998_00), until)

        assertEquals(stock, database.openHelper.readableDatabase.query("SELECT SUM(stock_quantity) FROM product_variants").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(movements, count(database, "stock_movements"))
        assertEquals(0, count(database, "invoices"))
        assertEquals(0, count(database, "customer_ledger"))
        // Invoices keep their own counter: the next invoice is still number 1.
        assertNull(database.operationDao().sequenceValue("INV|2026"))
    }

    @Test
    fun theQuotation_comesBackWithItsBillIntact() = runTest {
        val cart = cartOfShirt(3).let {
            it.copy(
                billDiscount = Discount.Amount(Money(25_00)),
                billDiscountReason = "Regular customer",
                customer = CustomerDetails(name = "Asha K", phone = "9876543210"),
            )
        }
        val saved = quotes.save(cart, Money(1_400_00), until)
        val back = quotes.get(saved.id)!!
        assertEquals(cart.billDiscount, back.cart.billDiscount)
        assertEquals("Regular customer", back.cart.billDiscountReason)
        assertEquals("Asha K", back.summary.customerName)
        assertEquals(3, back.summary.itemCount)
        assertEquals(Money(1_400_00), back.summary.total)
        assertEquals(cart.lines.single().productName, back.cart.lines.single().productName)
    }

    @Test
    fun listing_isNewestFirst_andDeletingRemovesOnlyThatOne() = runTest {
        val cart = cartOfShirt()
        val a = quotes.save(cart, Money(1), until)
        val b = quotes.save(cart, Money(2), until)
        assertEquals(listOf(b.id, a.id), quotes.observeAll().first().map { it.id })
        quotes.delete(a.id)
        assertEquals(listOf(b.id), quotes.observeAll().first().map { it.id })
        assertNull(quotes.get(a.id))
    }

    @Test
    fun converting_isRecordedOnce() = runTest {
        val saved = quotes.save(cartOfShirt(), Money(998_00), until)
        assertTrue(quotes.markConverted(saved.id, 42))
        assertFalse(quotes.markConverted(saved.id, 43))
        val row = quotes.observeAll().first().single()
        assertEquals(QuotationStatus.CONVERTED, row.status)
        assertEquals(42L, row.convertedInvoiceId)
        assertFalse(quotes.markConverted(9999, 1))
    }

    @Test
    fun anEmptyBill_cannotBeQuoted() = runTest {
        try {
            quotes.save(Cart(), Money.ZERO, until)
            fail("expected a refusal")
        } catch (expected: IllegalArgumentException) {
            assertEquals(0, count(database, "quotations"))
        }
    }

    @Test
    fun aDamagedQuotation_isStillListedSoItCanBeDeleted() = runTest {
        val saved = quotes.save(cartOfShirt(), Money(998_00), until)
        database.openHelper.writableDatabase.execSQL("UPDATE quotations SET payload = '{broken' WHERE id = ${saved.id}")
        assertNull(quotes.get(saved.id))
        assertNotNull(quotes.observeAll().first().singleOrNull())
        quotes.delete(saved.id)
        assertTrue(quotes.observeAll().first().isEmpty())
    }
}
