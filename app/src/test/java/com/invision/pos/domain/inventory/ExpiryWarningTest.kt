package com.invision.pos.domain.inventory

import com.invision.pos.domain.model.ExpiryWarning
import com.invision.pos.domain.model.StockBatch
import com.invision.pos.domain.reports.ExpiryWindow
import com.invision.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryWarningTest {

    private val today = TestData.today

    @Test
    fun choices_areSevenFifteenAndThirty_anythingElseFallsBackToThirty() {
        assertEquals(listOf(7, 15, 30), ExpiryWarning.CHOICES)
        assertEquals(30, ExpiryWarning.DEFAULT_DAYS)
        for (days in ExpiryWarning.CHOICES) assertEquals(days, ExpiryWarning.coerce(days))
        assertEquals(30, ExpiryWarning.coerce(0))
        assertEquals(30, ExpiryWarning.coerce(10))
        assertEquals(30, ExpiryWarning.coerce(-5))
    }

    @Test
    fun theWindowMatchesTheSetting() {
        assertEquals(ExpiryWindow.WITHIN_7_DAYS, ExpiryWindow.forWarningDays(7))
        assertEquals(ExpiryWindow.WITHIN_15_DAYS, ExpiryWindow.forWarningDays(15))
        assertEquals(ExpiryWindow.WITHIN_30_DAYS, ExpiryWindow.forWarningDays(30))
        assertEquals(ExpiryWindow.WITHIN_30_DAYS, ExpiryWindow.forWarningDays(99))
    }

    @Test
    fun fifteenDayWindow_includesExpiredAndNearBatches_butNotLaterOnesOrUndated() {
        fun batch(offset: Long?) = StockBatch(id = 1, variantId = 1, expiryDate = offset?.let { today.plusDays(it) }, quantity = 1)
        assertTrue(ExpiryWindow.WITHIN_15_DAYS.includes(batch(-3), today))
        assertTrue(ExpiryWindow.WITHIN_15_DAYS.includes(batch(15), today))
        assertFalse(ExpiryWindow.WITHIN_15_DAYS.includes(batch(16), today))
        assertFalse(ExpiryWindow.WITHIN_15_DAYS.includes(batch(null), today))
        // Between the 7 and 30 day windows.
        assertFalse(ExpiryWindow.WITHIN_7_DAYS.includes(batch(10), today))
        assertTrue(ExpiryWindow.WITHIN_30_DAYS.includes(batch(10), today))
    }

    @Test
    fun theExpiringFilter_followsTheSetting() {
        // Mango Juice expires in 10 days; the biscuits in 90.
        val products = listOf(TestData.biscuits(), TestData.juice(stock = 5))
        val query = InventoryQuery(stockFilter = StockFilter.EXPIRING_SOON)
        fun names(days: Int) = InventoryFilter.apply(products, query, today, days).map { it.name }
        assertEquals(emptyList<String>(), names(7))
        assertEquals(listOf("Mango Juice"), names(15))
        assertEquals(listOf("Mango Juice"), names(30))
    }

    @Test
    fun theHomeCounts_followTheSetting_andExpiredStockIsCountedOnItsOwn() {
        val products = listOf(
            TestData.biscuits(expiry = today.minusDays(1)), // expired
            TestData.juice(stock = 5), // 10 days
            TestData.flour(expiry = today.plusDays(25)),
        )
        fun summary(days: Int) = InventorySummaryCalculator.calculate(products, today, warningDays = days)
        assertEquals(0, summary(7).expiringSoonCount)
        assertEquals(1, summary(15).expiringSoonCount)
        assertEquals(2, summary(30).expiringSoonCount)
        for (days in ExpiryWarning.CHOICES) assertEquals(1, summary(days).expiredCount)
        // The expired item leads the alert list whatever the window is.
        assertEquals(StockAlertType.EXPIRED, summary(7).alerts.first().type)
        assertTrue(summary(7).alerts.none { it.type == StockAlertType.EXPIRING_SOON })
    }

    @Test
    fun withoutAWindow_thirtyDaysIsStillTheDefault() {
        val products = listOf(TestData.juice(stock = 5), TestData.flour(expiry = today.plusDays(25)))
        assertEquals(2, InventorySummaryCalculator.calculate(products, today).expiringSoonCount)
        assertEquals(2, InventoryFilter.apply(products, InventoryQuery(stockFilter = StockFilter.EXPIRING_SOON), today).size)
    }
}
