package com.auxo.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.repository.ReturnResult
import com.auxo.pos.data.repository.RoomBillingRepository
import com.auxo.pos.data.repository.RoomInventoryRepository
import com.auxo.pos.data.repository.SaleResult
import com.auxo.pos.data.repository.SaveProductResult
import com.auxo.pos.data.repository.StockChangeResult
import com.auxo.pos.domain.billing.BillCalculator
import com.auxo.pos.domain.billing.Cart
import com.auxo.pos.domain.billing.CartLine
import com.auxo.pos.domain.billing.ReturnBuild
import com.auxo.pos.domain.billing.ReturnBuilder
import com.auxo.pos.domain.billing.SaleBuilder
import com.auxo.pos.domain.billing.taxModeFor
import com.auxo.pos.domain.model.BatchDetails
import com.auxo.pos.domain.model.Invoice
import com.auxo.pos.domain.model.PaymentMethod
import com.auxo.pos.domain.model.PaymentRecord
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.StockChangeReason
import com.auxo.pos.domain.model.StockMovement
import com.auxo.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/**
 * Stock correctness against the real Room database: batches, expiry, wastage and
 * repeated requests. After every operation each variant's stock must equal the
 * sum of its movements, and for food, beverages and ingredients the sum of its batches.
 */
@RunWith(AndroidJUnit4::class)
class RoomStockTest {

    private lateinit var database: AuxoDatabase
    private lateinit var inventory: RoomInventoryRepository
    private lateinit var billing: RoomBillingRepository
    private val today = TestData.today

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

    private suspend fun reload(product: Product): Product = inventory.getProduct(product.id)!!

    private fun sale(product: Product, quantity: Int): Invoice {
        val cart = Cart(lines = listOf(CartLine.from(product, product.variants[0], quantity = quantity)))
        val totals = BillCalculator.calculate(cart, TestData.settings.taxModeFor(cart))
        return SaleBuilder.build(cart, totals, TestData.settings, listOf(PaymentRecord(method = PaymentMethod.CASH, amount = totals.grandTotal)), TestData.clock.millis())
    }

    private suspend fun receive(product: Product, quantity: Int, batch: String, expiryInDays: Long?, requestId: String? = null): StockChangeResult =
        inventory.recordStockChange(
            StockMovement(variantId = product.variants[0].id, quantityChange = quantity, reason = StockChangeReason.RECEIVED, createdAtMillis = TestData.clock.millis()),
            BatchDetails(batch, expiryInDays?.let { today.plusDays(it) }),
            requestId,
        )

    /** Stock = movements, and stock = batches for batch-tracked items. */
    private suspend fun assertConsistent(product: Product) {
        val dao = database.inventoryDao()
        val stored = reload(product)
        for (variant in stored.variants) {
            assertEquals("movements of ${variant.id}", variant.stockQuantity, dao.movementTotal(variant.id))
            if (stored.category.tracksExpiry) {
                assertEquals("batches of ${variant.id}", variant.stockQuantity, dao.batchTotal(variant.id))
            }
            assertTrue(variant.batches.all { it.quantity > 0 })
        }
    }

    @Test
    fun openingStock_ofFood_becomesTheFirstBatch() = runTest {
        val biscuits = saveNew(TestData.biscuits())
        val batch = biscuits.variants.single().batches.single()
        assertEquals(40, batch.quantity)
        assertEquals(today.plusDays(90), batch.expiryDate)
        assertConsistent(biscuits)
        // Garments are not kept in batches.
        val shirt = saveNew(TestData.tShirt())
        assertTrue(shirt.variants.all { it.batches.isEmpty() })
        assertConsistent(shirt)
    }

    @Test
    fun sale_takesTheBatchThatExpiresFirst() = runTest {
        val biscuits = saveNew(TestData.biscuits()) // 40, expiring in 90 days
        assertEquals(StockChangeResult.Applied, receive(biscuits, 20, "B2", expiryInDays = 10))
        val fresh = reload(biscuits)
        assertEquals(listOf(today.plusDays(10), today.plusDays(90)), fresh.variants[0].batches.map { it.expiryDate })
        assertEquals("B2", fresh.variants[0].batchNumber) // the next batch to sell

        val result = billing.recordSale(sale(fresh, 25), "INV") as SaleResult.Recorded
        val after = reload(biscuits).variants[0]
        assertEquals(35, after.stockQuantity)
        assertEquals(listOf(35), after.batches.map { it.quantity }) // B2 used up, 5 from the older batch
        assertEquals(today.plusDays(90), after.expiryDate)
        val sold = inventory.observeMovementHistory(0, Long.MAX_VALUE).first().filter { it.movement.reason == StockChangeReason.SOLD }
        assertEquals(setOf(-20, -5), sold.map { it.movement.quantityChange }.toSet())
        assertTrue(sold.all { it.reference == result.number })
        assertConsistent(biscuits)
    }

    @Test
    fun expiredStock_isNeverSold_andIsWrittenOffAsWastage() = runTest {
        val biscuits = saveNew(TestData.biscuits(expiry = today.minusDays(1))) // 40 already expired
        receive(biscuits, 5, "FRESH", expiryInDays = 30)
        val stocked = reload(biscuits)
        assertEquals(45, stocked.variants[0].stockQuantity)
        assertEquals(5, stocked.variants[0].sellableQuantity(today))

        assertEquals(SaleResult.StockShortage(setOf(stocked.variants[0].id)), billing.recordSale(sale(stocked, 6), "INV"))
        assertEquals(45, reload(biscuits).variants[0].stockQuantity) // nothing was taken
        assertTrue(billing.recordSale(sale(stocked, 5), "INV") is SaleResult.Recorded)

        val expired = inventory.observeBatches().first().single()
        assertEquals(40, expired.batch.quantity)
        assertTrue(expired.batch.isExpired(today))

        // More than the batch holds is refused and changes nothing.
        assertEquals(StockChangeResult.WouldGoNegative, inventory.writeOff(expired.batch.id, 41, StockChangeReason.EXPIRED, null, "w0"))
        assertEquals(40, reload(biscuits).variants[0].stockQuantity)

        assertEquals(StockChangeResult.Applied, inventory.writeOff(expired.batch.id, 40, StockChangeReason.EXPIRED, "Past date", "w1"))
        // The same request again doesn't write off twice.
        assertEquals(StockChangeResult.Applied, inventory.writeOff(expired.batch.id, 40, StockChangeReason.EXPIRED, "Past date", "w1"))
        val after = reload(biscuits).variants[0]
        assertEquals(0, after.stockQuantity)
        assertTrue(after.batches.isEmpty())
        val wastage = inventory.observeMovementHistory(0, Long.MAX_VALUE).first().filter { it.movement.reason.isWastage }
        assertEquals(listOf(-40), wastage.map { it.movement.quantityChange })
        assertEquals("Past date", wastage.single().movement.note)
        assertConsistent(biscuits)
    }

    @Test
    fun returns_goBackIntoTheBatchesTheSaleTookFrom() = runTest {
        val biscuits = saveNew(TestData.biscuits().let { it.copy(variants = listOf(it.variants[0].copy(stockQuantity = 10))) }) // A: 10, 90 days
        receive(biscuits, 10, "B", expiryInDays = 10) // B: 10, 10 days
        val saleId = (billing.recordSale(sale(reload(biscuits), 15), "INV") as SaleResult.Recorded).invoiceId // B 10 + A 5
        val original = billing.getInvoice(saleId)!!

        val note = (ReturnBuilder.build(original, mapOf(original.lines[0].id to 6), PaymentMethod.CASH, "Damaged box", TestData.clock.millis()) as ReturnBuild.Ready).creditNote
        assertTrue(billing.recordReturn(note, "CN") is ReturnResult.Recorded)

        // The 5 taken last (from A) go back to A, then 1 into B.
        val batches = reload(biscuits).variants[0].batches.associate { it.batchNumber to it.quantity }
        assertEquals(mapOf("B" to 1, null to 10), batches)
        assertEquals(11, reload(biscuits).variants[0].stockQuantity)
        assertConsistent(biscuits)

        // Returning more than is left to return is refused.
        val tooMany = ReturnBuilder.build(billing.getInvoice(saleId)!!, mapOf(original.lines[0].id to 10), PaymentMethod.CASH, "", TestData.clock.millis())
        assertTrue(tooMany !is ReturnBuild.Ready)
    }

    @Test
    fun stockChange_isAppliedOncePerRequest_andNeverGoesNegative() = runTest {
        val shirt = saveNew(TestData.tShirt()) // M: 8, L: 1
        val variant = shirt.variants[0]
        val add = StockMovement(variantId = variant.id, quantityChange = 5, reason = StockChangeReason.RECEIVED, createdAtMillis = 1)
        assertEquals(StockChangeResult.Applied, inventory.recordStockChange(add, requestId = "same"))
        assertEquals(StockChangeResult.Applied, inventory.recordStockChange(add, requestId = "same"))
        assertEquals(13, reload(shirt).variants[0].stockQuantity)

        val tooMuch = StockMovement(variantId = variant.id, quantityChange = -14, reason = StockChangeReason.DAMAGED, createdAtMillis = 2)
        assertEquals(StockChangeResult.WouldGoNegative, inventory.recordStockChange(tooMuch))
        assertEquals(13, reload(shirt).variants[0].stockQuantity)

        val missing = StockMovement(variantId = 9_999, quantityChange = 3, reason = StockChangeReason.RECEIVED, createdAtMillis = 3)
        assertEquals(StockChangeResult.ItemMissing, inventory.recordStockChange(missing))
        assertConsistent(shirt)
    }

    @Test
    fun gramCountedStock_isChangedInGrams() = runTest {
        val flour = saveNew(TestData.flour(grams = 2_000))
        val use = StockMovement(variantId = flour.variants[0].id, quantityChange = -750, reason = StockChangeReason.USED, createdAtMillis = 1)
        assertEquals(StockChangeResult.Applied, inventory.recordStockChange(use))
        assertEquals(1_250, reload(flour).variants[0].stockQuantity)
        assertEquals(1_250, reload(flour).variants[0].batches.single().quantity)
        assertConsistent(flour)
    }
}
