package com.invision.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.ReturnResult
import com.invision.pos.data.repository.RoomBillingRepository
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.RoomMenuRepository
import com.invision.pos.data.repository.SaleResult
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.domain.billing.BillCalculator
import com.invision.pos.domain.billing.Cart
import com.invision.pos.domain.billing.CartLine
import com.invision.pos.domain.billing.ReturnBuild
import com.invision.pos.domain.billing.ReturnBuilder
import com.invision.pos.domain.billing.SaleBuilder
import com.invision.pos.domain.billing.taxModeFor
import com.invision.pos.domain.model.Invoice
import com.invision.pos.domain.model.MenuItem
import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.PaymentMethod
import com.invision.pos.domain.model.PaymentRecord
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.RecipeLine
import com.invision.pos.domain.model.StockChangeReason
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

/** Restaurant dishes on bills, with and without recipe-based ingredient deductions. */
@RunWith(AndroidJUnit4::class)
class RoomMenuRecipeTest {

    private lateinit var database: InvisionDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository
    private lateinit var menu: RoomMenuRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InvisionDatabase::class.java).allowMainThreadQueries().build()
        inventory = RoomInventoryRepository(database, TestData.clock)
        billing = RoomBillingRepository(database) { ZoneOffset.UTC }
        menu = RoomMenuRepository(database, TestData.clock)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun saveNew(product: Product): Product {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        return inventory.getProduct((inventory.saveProduct(fresh) as SaveProductResult.Saved).productId)!!
    }

    private suspend fun stock(product: Product): Int = inventory.getProduct(product.id)!!.variants[0].stockQuantity

    private suspend fun dish(name: String, price: Long, deduct: Boolean, vararg recipe: Pair<Product, Int>): MenuItem {
        val id = menu.saveItem(
            MenuItem(
                name = name,
                section = "Breakfast",
                price = Money(price),
                deductIngredients = deduct,
                recipe = recipe.map { (product, quantity) -> RecipeLine(product.variants[0].id, quantity) },
            ),
        )
        return menu.getItem(id)!!
    }

    private fun bill(vararg lines: Pair<MenuItem, Int>): Invoice {
        val cart = Cart(lines = lines.map { (item, quantity) -> CartLine.forMenuItem(item, quantity) })
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        return SaleBuilder.build(cart, totals, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), TestData.clock.millis())
    }

    @Test
    fun sellingADish_usesItsRecipe() = runTest {
        val flour = saveNew(TestData.flour(grams = 5_000))
        val milk = saveNew(TestData.milk(millilitres = 2_000))
        val paratha = dish("Paratha", 6_000, deduct = true, flour to 120, milk to 30)
        val tea = dish("Tea", 2_000, deduct = false, milk to 100)

        val result = billing.recordSale(bill(paratha to 3, tea to 2), "INV") as SaleResult.Recorded
        assertEquals(5_000 - 360, stock(flour))
        assertEquals(2_000 - 90, stock(milk)) // tea doesn't deduct

        val invoice = billing.getInvoice(result.invoiceId)!!
        assertEquals(listOf(paratha.id, tea.id), invoice.lines.map { it.menuItemId })
        assertTrue(invoice.lines.all { it.variantId == null })
        assertEquals("996331", invoice.lines[0].hsnCode)
        assertEquals(500, invoice.lines[0].gstRateBasisPoints)

        val used = inventory.observeMovementHistory(0, Long.MAX_VALUE).first().filter { it.movement.reason == StockChangeReason.USED }
        assertEquals(setOf(-360, -90), used.map { it.movement.quantityChange }.toSet())
        assertTrue(used.all { it.reference == result.number })
    }

    @Test
    fun notEnoughIngredients_savesNothing() = runTest {
        val flour = saveNew(TestData.flour(grams = 500))
        val milk = saveNew(TestData.milk(millilitres = 2_000))
        val paratha = dish("Paratha", 6_000, deduct = true, flour to 120, milk to 30)

        assertEquals(SaleResult.IngredientShortage(setOf(flour.variants[0].id)), billing.recordSale(bill(paratha to 5), "INV"))
        assertEquals(500, stock(flour))
        assertEquals(2_000, stock(milk))
        assertTrue(billing.observeInvoices(0, Long.MAX_VALUE).first().isEmpty())
        assertTrue(billing.recordSale(bill(paratha to 4), "INV") is SaleResult.Recorded)
        assertEquals(20, stock(flour))
    }

    @Test
    fun expiredIngredients_areNotUsed() = runTest {
        val flour = saveNew(TestData.flour(grams = 5_000, expiry = TestData.today.minusDays(1)))
        val paratha = dish("Paratha", 6_000, deduct = true, flour to 120)
        assertEquals(SaleResult.IngredientShortage(setOf(flour.variants[0].id)), billing.recordSale(bill(paratha to 1), "INV"))
    }

    @Test
    fun returningADish_doesNotPutIngredientsBack() = runTest {
        val flour = saveNew(TestData.flour(grams = 1_000))
        val paratha = dish("Paratha", 6_000, deduct = true, flour to 100)
        val saleId = (billing.recordSale(bill(paratha to 2), "INV") as SaleResult.Recorded).invoiceId
        val sale = billing.getInvoice(saleId)!!
        val note = (ReturnBuilder.build(sale, mapOf(sale.lines.single().id to 1), PaymentMethod.CASH, "Cold", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
        assertTrue(billing.recordReturn(note, "CN") is ReturnResult.Recorded)
        assertEquals(800, stock(flour))
    }

    @Test
    fun editingADish_replacesItsRecipe_andDeletingKeepsPastBills() = runTest {
        val flour = saveNew(TestData.flour())
        val milk = saveNew(TestData.milk())
        val paratha = dish("Paratha", 6_000, deduct = true, flour to 120, milk to 30)
        menu.saveItem(paratha.copy(price = Money(6_500), recipe = listOf(RecipeLine(flour.variants[0].id, 150))))
        val edited = menu.getItem(paratha.id)!!
        assertEquals(Money(6_500), edited.price)
        assertEquals(listOf(RecipeLine(flour.variants[0].id, 150)), edited.recipe)

        val saleId = (billing.recordSale(bill(edited to 1), "INV") as SaleResult.Recorded).invoiceId
        assertEquals(5_000 - 150, stock(flour))
        menu.setAvailable(paratha.id, false)
        assertTrue(!menu.observeMenu().first().single().available)

        menu.deleteItem(paratha.id)
        assertNull(menu.getItem(paratha.id))
        assertEquals("Paratha", billing.getInvoice(saleId)!!.lines.single().productName)
        // Deleting an ingredient product removes it from recipes.
        val tea = dish("Tea", 2_000, deduct = true, milk to 100)
        inventory.deleteProduct(milk.id)
        assertTrue(menu.getItem(tea.id)!!.recipe.isEmpty())
    }
}
