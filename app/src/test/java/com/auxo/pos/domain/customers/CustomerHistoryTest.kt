package com.auxo.pos.domain.customers

import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.Money
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerHistoryTest {

    private val india = ZoneId.of("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(india).toInstant().toEpochMilli()

    private fun bill(id: Long, type: InvoiceType, millis: Long, minor: Long) =
        InvoiceSummary(id, "N-$id", type, millis, "Asha", null, Money(minor), 1)

    // Known figures: three sales over two days and one return.
    private val sale1 = bill(1, InvoiceType.SALE, at(2026, 6, 1, 10), 120_000)
    private val sale2 = bill(2, InvoiceType.SALE, at(2026, 6, 1, 18, 30), 30_000)
    private val sale3 = bill(3, InvoiceType.SALE, at(2026, 6, 5, 11), 50_050)
    private val credit = bill(4, InvoiceType.RETURN, at(2026, 6, 6, 9), 20_000)

    @Test
    fun noBills_isAnEmptyHistory() {
        val history = CustomerHistory.from(emptyList(), india)
        assertEquals(CustomerHistory.EMPTY, history)
        assertEquals(Money.ZERO, history.totalSpend)
        assertEquals(0, history.visitCount)
        assertNull(history.lastVisitMillis)
        assertNull(history.averageBill)
    }

    @Test
    fun theFigures_matchTheKnownTotals() {
        val history = CustomerHistory.from(listOf(sale1, credit, sale3, sale2), india)
        assertEquals(Money(200_050), history.salesTotal)
        assertEquals(Money(20_000), history.returnsTotal)
        assertEquals(Money(180_050), history.totalSpend) // sales less returns
        assertEquals(3, history.billCount)
        assertEquals(2, history.visitCount) // two bills on 1 June are one visit
        assertEquals(Money(66_683), history.averageBill) // 200,050 / 3 = 66,683.33, rounded to the paisa
    }

    @Test
    fun theDates_areTheFirstAndLastSale_notTheReturn() {
        val history = CustomerHistory.from(listOf(sale1, sale2, sale3, credit), india)
        assertEquals(sale1.createdAtMillis, history.firstVisitMillis)
        assertEquals(sale3.createdAtMillis, history.lastVisitMillis)
    }

    @Test
    fun billsAndReturns_areListedNewestFirst() {
        val history = CustomerHistory.from(listOf(sale1, sale3, sale2, credit), india)
        assertEquals(listOf(3L, 2L, 1L), history.purchases.map { it.id })
        assertEquals(listOf(4L), history.returns.map { it.id })
    }

    @Test
    fun billsAtTheSameInstant_keepAStableOrder() {
        val a = bill(10, InvoiceType.SALE, 5_000, 100)
        val b = bill(11, InvoiceType.SALE, 5_000, 100)
        assertEquals(listOf(11L, 10L), CustomerHistory.from(listOf(a, b), india).purchases.map { it.id })
    }

    @Test
    fun aVisit_isADayInTheShopsTimeZone() {
        // 23:50 and 00:10 India time are twenty minutes apart but on different days there, the same day in UTC.
        val lateNight = bill(1, InvoiceType.SALE, at(2026, 6, 1, 23, 50), 10_000)
        val afterMidnight = bill(2, InvoiceType.SALE, at(2026, 6, 2, 0, 10), 10_000)
        assertEquals(2, CustomerHistory.from(listOf(lateNight, afterMidnight), india).visitCount)
        assertEquals(1, CustomerHistory.from(listOf(lateNight, afterMidnight), ZoneOffset.UTC).visitCount)
    }

    @Test
    fun onlyReturns_giveNegativeSpendAndNoVisits() {
        val history = CustomerHistory.from(listOf(credit), india)
        assertEquals(Money(-20_000), history.totalSpend)
        assertEquals(0, history.visitCount)
        assertNull(history.averageBill)
        assertNull(history.lastVisitMillis)
    }

    @Test
    fun theAverage_roundsHalfUp() {
        val history = CustomerHistory.from(
            listOf(bill(1, InvoiceType.SALE, 1_000, 100), bill(2, InvoiceType.SALE, 2_000, 101)),
            india,
        )
        assertEquals(Money(101), history.averageBill) // 100.5 rounds up
    }

    @Test
    fun oneBill_isItsOwnAverage() {
        assertEquals(Money(120_000), CustomerHistory.from(listOf(sale1), india).averageBill)
    }
}
