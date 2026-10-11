package com.invision.pos.domain.model

import com.invision.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetQuantityTest {

    @Test
    fun parseAndFormat() {
        assertEquals("500 g", NetQuantity.parseOrNull("500", QuantityUnit.GRAM)?.format())
        assertEquals("1.25 L", NetQuantity.parseOrNull("1.25", QuantityUnit.LITRE)?.format())
        assertEquals("1.25", NetQuantity.parseOrNull("1.250", QuantityUnit.LITRE)?.amountText())
    }

    @Test
    fun rejectsZeroNegativeAndMalformed() {
        assertNull(NetQuantity.parseOrNull("0", QuantityUnit.GRAM))
        assertNull(NetQuantity.parseOrNull("-1", QuantityUnit.GRAM))
        assertNull(NetQuantity.parseOrNull("1.2345", QuantityUnit.GRAM))
        assertNull(NetQuantity.parseOrNull("", QuantityUnit.GRAM))
    }
}

class ProductModelTest {

    @Test
    fun variantStockStatus() {
        assertEquals(StockStatus.OUT_OF_STOCK, TestData.variant(stock = 0, lowStock = 2).stockStatus)
        assertEquals(StockStatus.LOW_STOCK, TestData.variant(stock = 2, lowStock = 2).stockStatus)
        assertEquals(StockStatus.IN_STOCK, TestData.variant(stock = 3, lowStock = 2).stockStatus)
    }

    @Test
    fun lowStock_isAutomaticBelowTwoUnits_whenNoThresholdIsSet() {
        // No threshold configured (0): the default level of 1 applies.
        assertEquals(StockStatus.OUT_OF_STOCK, TestData.variant(stock = 0, lowStock = 0).stockStatus)
        assertEquals(StockStatus.LOW_STOCK, TestData.variant(stock = 1, lowStock = 0).stockStatus)
        assertEquals(StockStatus.IN_STOCK, TestData.variant(stock = 2, lowStock = 0).stockStatus)
        assertTrue(TestData.variant(stock = 0, lowStock = 0).needsRestock)
        assertTrue(TestData.variant(stock = 1, lowStock = 0).needsRestock)
        assertFalse(TestData.variant(stock = 2, lowStock = 0).needsRestock)
    }

    @Test
    fun lowStock_followsCurrentStock_andKeepsAConfiguredThreshold() {
        val variant = TestData.variant(stock = 5, lowStock = 5)
        assertEquals(StockStatus.LOW_STOCK, variant.stockStatus)
        assertEquals(StockStatus.IN_STOCK, variant.copy(stockQuantity = 6).stockStatus)
        assertEquals(StockStatus.LOW_STOCK, variant.copy(stockQuantity = 1).stockStatus)
        assertEquals(5, variant.effectiveLowStockLevel)
        assertEquals(DEFAULT_LOW_STOCK_LEVEL, TestData.variant(lowStock = 0).effectiveLowStockLevel)
    }

    @Test
    fun productStockStatus_reflectsWorstVariant() {
        val product = TestData.tShirt() // M has 8, L has 1 with threshold 2
        assertEquals(StockStatus.LOW_STOCK, product.stockStatus)
        assertEquals(9, product.totalStock)
        assertEquals(StockStatus.OUT_OF_STOCK, TestData.juice(stock = 0).stockStatus)
    }

    @Test
    fun expiryChecks() {
        val today = TestData.today
        val variant = TestData.variant(expiry = today.plusDays(5))
        assertFalse(variant.isExpired(today))
        assertTrue(variant.expiresWithin(today, 30))
        assertFalse(variant.expiresWithin(today, 3))
        assertTrue(TestData.variant(expiry = today.minusDays(1)).isExpired(today))
        assertFalse(TestData.variant(expiry = null).expiresWithin(today, 30))
    }

    @Test
    fun attributesLabel_joinsSizeColourAndPack() {
        assertEquals("M · Navy", TestData.variant(size = "M", colour = "Navy").attributesLabel)
        assertEquals("200 g", TestData.biscuits().variants.first().attributesLabel)
        assertEquals("", TestData.variant().attributesLabel)
    }
}
