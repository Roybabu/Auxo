package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.ReturnBuild
import com.auxo.pos.domain.billing.ReturnBuilder
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.testutil.DataIntegrity
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/** Restaurant items sit in the catalogue: they bill like any product, and only the ones that count stock move it. */
@RunWith(AndroidJUnit4::class)
class RoomRestaurantTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuxoDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        billing = RoomBillingRepository(database) { ZoneOffset.UTC }
    }

    @After
    fun tearDown() = database.close()

    private suspend fun saveNew(product: Product): Product {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        val id = (inventory.saveProduct(fresh) as SaveProductResult.Saved).productId
        return inventory.getProduct(id)!!
    }

    private fun sale(product: Product, quantity: Int): Invoice {
        val cart = Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity = quantity)))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        return SaleBuilder.build(cart, totals, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), TestData.clock.millis())
    }

    private fun movements() = DataIntegrity.count(database, "stock_movements")

    @Test
    fun theDishKeepsItsSubcategory_andNoStockCount() = runTest {
        val dosa = saveNew(TestData.dish(subcategory = "Breakfast", available = false))
        assertEquals("Breakfast", dosa.subcategory)
        assertEquals(false, dosa.available)
        assertEquals(false, dosa.tracksStock)
    }

    @Test
    fun sellingAnItemWithoutAStockCount_neverMovesStock_orRunsOut() = runTest {
        val dosa = saveNew(TestData.dish())
        val before = movements()
        val result = billing.recordSale(sale(dosa, 5), "INV")
        assertTrue(result is SaleResult.Recorded)
        assertEquals(before, movements())
        assertEquals(0, inventory.getProduct(dosa.id)!!.variants.single().stockQuantity)
    }

    @Test
    fun returningAnItemWithoutAStockCount_putsNothingBackOnTheShelf() = runTest {
        val dosa = saveNew(TestData.dish())
        val sold = billing.recordSale(sale(dosa, 2), "INV") as SaleResult.Recorded
        val original = billing.getInvoice(sold.invoiceId)!!
        val note = (ReturnBuilder.build(original, mapOf(original.lines.single().id to 1), PaymentMethod.CASH, "x", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
        billing.recordReturn(note, "CN")
        assertEquals(0, inventory.getProduct(dosa.id)!!.variants.single().stockQuantity)
        assertEquals(0, movements())
    }

    @Test
    fun anItemThatCountsStock_isTakenOffTheShelfLikeAnyProduct() = runTest {
        val cake = saveNew(TestData.dish(id = 41, name = "Pastry", tracksStock = true, stock = 5))
        billing.recordSale(sale(cake, 2), "INV")
        assertEquals(3, inventory.getProduct(cake.id)!!.variants.single().stockQuantity)
    }
}
