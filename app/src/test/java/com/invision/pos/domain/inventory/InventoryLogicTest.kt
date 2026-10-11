package com.invision.pos.domain.inventory

import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.ProductCategory
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryFilterTest {

    private val today = TestData.today
    private val products = listOf(TestData.juice(), TestData.tShirt(), TestData.biscuits())

    private fun names(query: InventoryQuery) = InventoryFilter.apply(products, query, today).map { it.name }

    @Test
    fun noFilter_returnsAllSortedByName() {
        assertEquals(listOf("Butter Biscuits", "Cotton T-Shirt", "Mango Juice"), names(InventoryQuery()))
    }

    @Test
    fun textSearch_matchesNameBrandColourAndExactBarcode() {
        assertEquals(listOf("Cotton T-Shirt"), names(InventoryQuery(text = "shirt")))
        assertEquals(listOf("Butter Biscuits"), names(InventoryQuery(text = "good bake")))
        assertEquals(listOf("Cotton T-Shirt"), names(InventoryQuery(text = "navy")))
        assertEquals(listOf("Mango Juice"), names(InventoryQuery(text = TestData.EAN[3])))
        assertEquals(emptyList<String>(), names(InventoryQuery(text = "jeans")))
    }

    @Test
    fun categoryFilter() {
        assertEquals(listOf("Butter Biscuits"), names(InventoryQuery(category = ProductCategory.PACKAGED_FOOD)))
    }

    @Test
    fun stockFilters() {
        assertEquals(listOf("Cotton T-Shirt"), names(InventoryQuery(stockFilter = StockFilter.LOW_STOCK)))
        assertEquals(listOf("Mango Juice"), names(InventoryQuery(stockFilter = StockFilter.OUT_OF_STOCK)))
        // Juice expires in 10 days; biscuits in 90.
        assertEquals(listOf("Mango Juice"), names(InventoryQuery(stockFilter = StockFilter.EXPIRING_SOON)))
    }

    @Test
    fun filtersCombine() {
        val query = InventoryQuery(text = "juice", category = ProductCategory.GARMENT)
        assertTrue(names(query).isEmpty())
        assertTrue(query.isFiltered)
        assertTrue(!InventoryQuery().isFiltered)
    }
}

class InventorySummaryCalculatorTest {

    @Test
    fun emptyInventory() {
        val summary = InventorySummaryCalculator.calculate(emptyList(), TestData.today)
        assertEquals(InventorySummary.EMPTY, summary)
    }

    @Test
    fun countsValuesAndAlerts() {
        val expiredBiscuits = TestData.biscuits(expiry = TestData.today.minusDays(1))
        val products = listOf(TestData.tShirt(), expiredBiscuits, TestData.juice(stock = 0))
        val summary = InventorySummaryCalculator.calculate(products, TestData.today)

        assertEquals(3, summary.productCount)
        assertEquals(4, summary.variantCount)
        assertEquals(8 + 1 + 40 + 0, summary.unitsInStock)
        // Cost: 9 shirts x 250.00 + 40 biscuits x 22.00 (juice has no cost)
        assertEquals(Money(9 * 25_000L + 40 * 2_200L), summary.stockValueAtCost)
        assertEquals(Money(9 * 49_900L + 40 * 3_000L), summary.stockValueAtPrice)
        assertEquals(1, summary.lowStockCount)
        assertEquals(1, summary.outOfStockCount)
        assertEquals(1, summary.expiredCount)
        assertEquals(1, summary.expiringSoonCount)

        // Most urgent first: expired, out of stock, then low stock.
        assertEquals(
            listOf(StockAlertType.EXPIRED, StockAlertType.OUT_OF_STOCK, StockAlertType.LOW_STOCK),
            summary.alerts.map { it.type },
        )
        assertEquals("L · Navy", summary.alerts.last().variantLabel)
    }

    @Test
    fun lowStock_countsUnitsOfZeroAndOne_butNotTwo_whenNoThresholdIsSet() {
        val product = TestData.tShirt().copy(
            variants = listOf(
                TestData.variant(id = 11, barcode = TestData.EAN[0], stock = 1, lowStock = 0),
                TestData.variant(id = 12, barcode = TestData.EAN[1], stock = 2, lowStock = 0),
                TestData.variant(id = 13, barcode = TestData.EAN[2], stock = 0, lowStock = 0),
            ),
        )
        val summary = InventorySummaryCalculator.calculate(listOf(product), TestData.today)
        assertEquals(1, summary.lowStockCount)
        assertEquals(1, summary.outOfStockCount)
    }

    @Test
    fun alertsAreCapped() {
        val products = (1L..10L).map { TestData.juice(id = it, stock = 0) }
        val summary = InventorySummaryCalculator.calculate(products, TestData.today, maxAlerts = 3)
        assertEquals(3, summary.alerts.size)
        assertEquals(10, summary.outOfStockCount)
    }
}

class StockAdjustmentCalculatorTest {

    private fun calc(reason: StockChangeReason, text: String, current: Int = 10) =
        StockAdjustmentCalculator.calculate(reason, text, current)

    @Test
    fun receivedAndReturnedAddStock() {
        assertEquals(StockAdjustment.Valid(5, StockChangeReason.RECEIVED), calc(StockChangeReason.RECEIVED, "5"))
        assertEquals(StockAdjustment.Valid(2, StockChangeReason.RETURNED), calc(StockChangeReason.RETURNED, "2"))
    }

    @Test
    fun soldAndDamagedRemoveStock() {
        assertEquals(StockAdjustment.Valid(-3, StockChangeReason.SOLD), calc(StockChangeReason.SOLD, "3"))
        assertEquals(StockAdjustment.Valid(-10, StockChangeReason.DAMAGED), calc(StockChangeReason.DAMAGED, "10"))
    }

    @Test
    fun correctionSetsTheCountedQuantity() {
        assertEquals(StockAdjustment.Valid(-4, StockChangeReason.CORRECTION), calc(StockChangeReason.CORRECTION, "6"))
        assertEquals(StockAdjustment.Valid(-10, StockChangeReason.CORRECTION), calc(StockChangeReason.CORRECTION, "0"))
        assertEquals(
            StockAdjustment.Invalid(StockAdjustmentError.NO_CHANGE),
            calc(StockChangeReason.CORRECTION, "10"),
        )
    }

    @Test
    fun invalidInput() {
        assertEquals(StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_REQUIRED), calc(StockChangeReason.RECEIVED, " "))
        assertEquals(StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_INVALID), calc(StockChangeReason.RECEIVED, "2.5"))
        assertEquals(StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_INVALID), calc(StockChangeReason.RECEIVED, "0"))
        assertEquals(StockAdjustment.Invalid(StockAdjustmentError.QUANTITY_TOO_LARGE), calc(StockChangeReason.RECEIVED, "99999999999"))
        assertEquals(StockAdjustment.Invalid(StockAdjustmentError.WOULD_GO_NEGATIVE), calc(StockChangeReason.SOLD, "11"))
    }
}
