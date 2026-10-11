package com.invision.pos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.invision.pos.data.local.InvisionDatabase
import com.invision.pos.data.repository.RoomInventoryRepository
import com.invision.pos.data.repository.SaveProductResult
import com.invision.pos.data.repository.StockChangeResult
import com.invision.pos.domain.model.Product
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockMovement
import com.invision.pos.testutil.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Room database (in memory) through the repository. */
@RunWith(AndroidJUnit4::class)
class RoomInventoryRepositoryTest {

    private lateinit var database: InvisionDatabase
    private lateinit var repository: RoomInventoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InvisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomInventoryRepository(database, TestData.clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Saves [product] as new (ids cleared) and returns the stored copy. */
    private suspend fun saveNew(product: Product): Product {
        val fresh = product.copy(id = 0, variants = product.variants.map { it.copy(id = 0, productId = 0) })
        val result = repository.saveProduct(fresh) as SaveProductResult.Saved
        return repository.getProduct(result.productId)!!
    }

    @Test
    fun savedProduct_roundTripsAllFields() = runTest {
        val stored = saveNew(TestData.biscuits())

        assertEquals("Butter Biscuits", stored.name)
        assertEquals(TestData.biscuits().dietType, stored.dietType)
        val variant = stored.variants.single()
        val expected = TestData.biscuits().variants.single()
        assertEquals(expected.netQuantity, variant.netQuantity)
        assertEquals(expected.sellingPrice, variant.sellingPrice)
        assertEquals(expected.mrp, variant.mrp)
        assertEquals(expected.expiryDate, variant.expiryDate)
        assertEquals(40, variant.stockQuantity)
        assertEquals(TestData.clock.millis(), stored.createdAtMillis)
    }

    @Test
    fun openingStock_isRecordedAsMovement() = runTest {
        val stored = saveNew(TestData.tShirt())
        val movements = repository.observeStockMovements(stored.id).first()
        assertEquals(2, movements.size)
        assertTrue(movements.all { it.reason == StockChangeReason.INITIAL })
        assertEquals(setOf(8, 1), movements.map { it.quantityChange }.toSet())
    }

    @Test
    fun barcodeOrSkuOfAnotherProduct_isAConflict() = runTest {
        saveNew(TestData.tShirt())
        val clash = TestData.biscuits().let { p ->
            p.copy(variants = p.variants.map { it.copy(barcode = TestData.EAN[0]) })
        }
        val result = repository.saveProduct(clash.copy(id = 0, variants = clash.variants.map { it.copy(id = 0) }))
        assertEquals(SaveProductResult.Conflict(barcodesInUse = setOf(TestData.EAN[0])), result)
    }

    @Test
    fun editing_updatesFieldsKeepsStockAndRemovesDroppedVariants() = runTest {
        val stored = saveNew(TestData.tShirt())
        val medium = stored.variants.first { it.size == "M" }
        val large = stored.variants.first { it.size == "L" }

        // Sell all large shirts so that variant can be removed, then drop it and rename.
        repository.recordStockChange(StockMovement(variantId = large.id, quantityChange = -1, reason = StockChangeReason.SOLD, createdAtMillis = 1))
        val edited = stored.copy(
            name = "Crew T-Shirt",
            variants = listOf(medium.copy(sellingPrice = medium.sellingPrice + medium.sellingPrice, stockQuantity = 999)),
        )
        assertTrue(repository.saveProduct(edited) is SaveProductResult.Saved)

        val reloaded = repository.getProduct(stored.id)!!
        assertEquals("Crew T-Shirt", reloaded.name)
        assertEquals(1, reloaded.variants.size)
        assertEquals(8, reloaded.variants.single().stockQuantity) // stock is not overwritten by edits
        assertEquals(medium.sellingPrice + medium.sellingPrice, reloaded.variants.single().sellingPrice)
    }

    @Test
    fun stockChange_cannotGoNegative() = runTest {
        val stored = saveNew(TestData.tShirt())
        val large = stored.variants.first { it.size == "L" } // 1 in stock

        val later = TestData.clock.millis() + 60_000
        val tooMany = StockMovement(variantId = large.id, quantityChange = -2, reason = StockChangeReason.SOLD, createdAtMillis = later)
        assertEquals(StockChangeResult.WouldGoNegative, repository.recordStockChange(tooMany))
        assertEquals(1, repository.getProduct(stored.id)!!.variants.first { it.id == large.id }.stockQuantity)

        val received = StockMovement(variantId = large.id, quantityChange = 5, reason = StockChangeReason.RECEIVED, createdAtMillis = later)
        assertEquals(StockChangeResult.Applied, repository.recordStockChange(received))
        assertEquals(6, repository.getProduct(stored.id)!!.variants.first { it.id == large.id }.stockQuantity)
        // Newest first.
        assertEquals(StockChangeReason.RECEIVED, repository.observeStockMovements(stored.id).first().first().reason)
    }

    @Test
    fun findByBarcode_andDelete() = runTest {
        val stored = saveNew(TestData.juice(stock = 3))
        val match = repository.findByBarcode(TestData.EAN[3])
        assertNotNull(match)
        assertEquals(stored.id, match!!.product.id)
        assertTrue(repository.isBarcodeInUse(TestData.EAN[3]))

        repository.deleteProduct(stored.id)
        assertNull(repository.getProduct(stored.id))
        assertNull(repository.findByBarcode(TestData.EAN[3]))
        assertTrue(repository.observeStockMovements(stored.id).first().isEmpty())
        assertTrue(repository.observeProducts().first().isEmpty())
    }

    @Test
    fun findByBarcode_matchesUpcSentAsEan13() = runTest {
        val juice = TestData.juice(stock = 3)
        val stored = saveNew(juice.copy(variants = juice.variants.map { it.copy(barcode = "036000291452") }))
        assertEquals(stored.id, repository.findByBarcode("0036000291452")?.product?.id)
        assertEquals(stored.id, repository.findByBarcode("036000291452")?.product?.id)
    }

    @Test
    fun observeProducts_isSortedByName() = runTest {
        saveNew(TestData.juice())
        saveNew(TestData.tShirt())
        saveNew(TestData.biscuits())
        assertEquals(
            listOf("Butter Biscuits", "Cotton T-Shirt", "Mango Juice"),
            repository.observeProducts().first().map { it.name },
        )
    }
}
