package com.invision.pos.domain.inventory

import com.invision.pos.domain.model.Money
import com.invision.pos.domain.model.StockBatch
import com.invision.pos.domain.model.StockChangeReason
import com.invision.pos.domain.model.StockUnit
import com.invision.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchAllocatorTest {

    @Test
    fun allocate_takesEarliestBatchFirst_andSpillsIntoTheNext() {
        val takes = BatchAllocator.allocate(listOf(BatchStock(1, 3), BatchStock(2, 10)), 5)
        assertEquals(listOf(BatchTake(1, 3), BatchTake(2, 2)), takes)
    }

    @Test
    fun allocate_skipsEmptyBatches_andRefusesWhenShort() {
        assertEquals(listOf(BatchTake(2, 4)), BatchAllocator.allocate(listOf(BatchStock(1, 0), BatchStock(2, 4)), 4))
        assertNull(BatchAllocator.allocate(listOf(BatchStock(1, 2), BatchStock(2, 2)), 5))
    }

    @Test
    fun placeReturn_fillsTheBatchesTheSaleTookFrom_lastTakenFirst() {
        // The sale took 3 from batch 1 and then 2 from batch 2.
        val placement = BatchAllocator.placeReturn(listOf(BatchTake(2, 2), BatchTake(1, 3)), emptyMap(), 4)
        assertEquals(listOf(BatchTake(2, 2), BatchTake(1, 2)), placement.takes)
        assertEquals(0, placement.unplaced)
    }

    @Test
    fun placeReturn_countsEarlierReturns_andLeavesTheRestUnplaced() {
        val placement = BatchAllocator.placeReturn(listOf(BatchTake(2, 2), BatchTake(1, 3)), mapOf(2L to 2, 1L to 2), 3)
        assertEquals(listOf(BatchTake(1, 1)), placement.takes)
        assertEquals(2, placement.unplaced)
    }
}

class StockUnitTest {

    @Test
    fun parse_readsEntryUnits_intoBaseUnits() {
        assertEquals(1_250, StockUnit.GRAM.parse("1.25"))
        assertEquals(150, StockUnit.MILLILITRE.parse("0.15"))
        assertEquals(12, StockUnit.UNIT.parse("12"))
    }

    @Test
    fun parse_rejectsBadInput() {
        assertNull(StockUnit.UNIT.parse("1.5")) // pieces are whole
        assertNull(StockUnit.GRAM.parse("0.0005")) // finer than a gram
        assertNull(StockUnit.GRAM.parse("0"))
        assertEquals(0, StockUnit.GRAM.parse("0", allowZero = true))
        assertNull(StockUnit.GRAM.parse("-1"))
        assertNull(StockUnit.GRAM.parse("abc"))
        assertNull(StockUnit.UNIT.parse("1000001"))
    }

    @Test
    fun format_switchesToKilogramsFromOneThousandGrams() {
        assertEquals("750 g", StockUnit.GRAM.format(750))
        assertEquals("1.25 kg", StockUnit.GRAM.format(1_250))
        assertEquals("2 L", StockUnit.MILLILITRE.format(2_000))
        assertEquals("12", StockUnit.UNIT.format(12))
        assertEquals("1.25", StockUnit.GRAM.toEntryText(1_250))
    }

    @Test
    fun costOf_pricesPartOfAKilogram_roundingHalfUp() {
        // ₹45.00 per kg: 250 g costs ₹11.25, 333 g costs ₹14.985 -> ₹14.99.
        assertEquals(Money(1_125), StockUnit.GRAM.costOf(250, Money(4_500)))
        assertEquals(Money(1_499), StockUnit.GRAM.costOf(333, Money(4_500)))
        assertEquals(Money(-1_125), StockUnit.GRAM.costOf(-250, Money(4_500)))
        assertEquals(Money(7_500), StockUnit.UNIT.costOf(3, Money(2_500)))
    }
}

class StockBatchTest {

    @Test
    fun expiredBatches_areNotSellable() {
        val today = TestData.today
        val variant = TestData.biscuits().variants.single().copy(
            stockQuantity = 15,
            batches = listOf(
                StockBatch(id = 1, variantId = 21, expiryDate = today.minusDays(1), quantity = 5),
                StockBatch(id = 2, variantId = 21, expiryDate = today, quantity = 4),
                StockBatch(id = 3, variantId = 21, expiryDate = null, quantity = 6),
            ),
        )
        assertEquals(5, variant.expiredQuantity(today))
        assertEquals(10, variant.sellableQuantity(today)) // a batch expiring today can still be sold
        assertTrue(variant.batches[1].expiresWithin(today, 0))
    }

    @Test
    fun wastageReasons_areTheWriteOffReasons() {
        assertEquals(StockChangeReason.WASTAGE.toSet(), StockChangeReason.entries.filter { it.isWastage }.toSet())
    }
}

class ReorderPlannerTest {

    private val shirt = TestData.tShirt() // M: 8 in stock (low at 2), L: 1 in stock (low at 2)
    private val juice = TestData.juice(stock = 0)
    private val biscuits = TestData.biscuits() // 40 in stock, low at 10

    @Test
    fun suggestions_listOutOfStockFirst_thenLowStock() {
        val suggestions = ReorderPlanner.suggestions(listOf(shirt, juice, biscuits))
        assertEquals(listOf(juice.variants[0].id, shirt.variants[1].id), suggestions.map { it.variant.id })
        assertTrue(suggestions.first().isOutOfStock)
    }

    @Test
    fun suggestedQuantity_usesReorderQuantity_orTopsUpToTwiceTheLowLevel() {
        assertEquals(3, ReorderPlanner.suggestedQuantity(shirt.variants[1])) // 2 x 2 - 1
        assertEquals(24, ReorderPlanner.suggestedQuantity(shirt.variants[1].copy(reorderQuantity = 24)))
        assertEquals(1, ReorderPlanner.suggestedQuantity(shirt.variants[1].copy(lowStockThreshold = 0, stockQuantity = 0)))
    }

    @Test
    fun draftOrders_groupBySupplier_andUseTheFallbackForTheRest() {
        val withSupplier = shirt.copy(preferredSupplierId = 7)
        val suggestions = ReorderPlanner.suggestions(listOf(withSupplier, juice))
        val orders = ReorderPlanner.draftOrders(suggestions, fallbackSupplierId = 9)
        assertEquals(setOf(7L, 9L), orders.map { it.supplierId }.toSet())
        val shirtOrder = orders.single { it.supplierId == 7L }
        assertEquals(Money(25_000), shirtOrder.lines.single().unitCost)
        assertEquals(3, shirtOrder.lines.single().quantityOrdered)
        // Without a fallback, items with no supplier are left out.
        assertEquals(listOf(7L), ReorderPlanner.draftOrders(suggestions, null).map { it.supplierId })
    }
}

class ItemSearchTest {

    @Test
    fun search_matchesNameOrBarcode_andFiltersProducts() {
        val products = listOf(TestData.tShirt(), TestData.biscuits(), TestData.flour())
        assertEquals(listOf("Butter Biscuits"), ItemSearch.search(products, "butter").map { it.product.name }.distinct())
        val byBarcode = ItemSearch.search(products, TestData.EAN[1])
        assertEquals(TestData.tShirt().variants[1].id, byBarcode.single().variant.id)
        val ingredientsOnly = ItemSearch.search(products, "", include = { !it.category.isSold })
        assertEquals(listOf("Wheat Flour"), ingredientsOnly.map { it.product.name })
    }
}

class StockAdjustmentUnitsTest {

    @Test
    fun ingredientAdjustments_areTypedInKilograms() {
        val used = StockAdjustmentCalculator.calculate(StockChangeReason.USED, "0.25", currentStock = 1_000, unit = StockUnit.GRAM)
        assertEquals(StockAdjustment.Valid(-250, StockChangeReason.USED), used)
        val counted = StockAdjustmentCalculator.calculate(StockChangeReason.CORRECTION, "0.8", currentStock = 1_000, unit = StockUnit.GRAM)
        assertEquals(StockAdjustment.Valid(-200, StockChangeReason.CORRECTION), counted)
    }

    @Test
    fun ingredients_offerKitchenReasons() {
        val reasons = StockAdjustmentCalculator.manualReasonsFor(com.invision.pos.domain.model.ProductCategory.INGREDIENT)
        assertTrue(StockChangeReason.USED in reasons)
        assertTrue(StockChangeReason.SOLD !in reasons)
    }
}
