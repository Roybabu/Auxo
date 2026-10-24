// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.inventory

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auxo.pos.domain.model.Money
import com.auxo.pos.testutil.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockCountsTest {

    private val shirt = TestData.tShirt() // M: 8 pcs, L: 1 pc, both cost 250.00
    private val flour = TestData.flour(grams = 5_000) // cost 45.00 per kg
    private val products = listOf(shirt, flour)

    @Test
    fun aLine_knowsItsDifference() {
        assertEquals(-3, CountLine(1, expected = 8, counted = 5).difference)
        assertEquals(2, CountLine(1, expected = 8, counted = 10).difference)
        assertEquals(0, CountLine(1, expected = 8, counted = 8).difference)
    }

    @Test
    fun typedQuantities_areParsedInTheEntryUnit_zeroIsAllowed() {
        assertEquals(0, StockCounts.parseCounted("0", shirt))
        assertEquals(12, StockCounts.parseCounted(" 12 ", shirt))
        assertNull(StockCounts.parseCounted("", shirt))
        assertNull(StockCounts.parseCounted("1.5", shirt)) // pieces are whole numbers
        assertNull(StockCounts.parseCounted("-2", shirt))
        assertNull(StockCounts.parseCounted("abc", shirt))
        assertEquals(2_500, StockCounts.parseCounted("2.5", flour)) // kg to grams
        assertNull(StockCounts.parseCounted("2.5555", flour))
    }

    @Test
    fun rows_joinLinesWithProducts_andDropDeletedOnes() {
        val count = StockCount(
            id = 1,
            startedAtMillis = 0,
            lines = listOf(CountLine(shirt.variants[0].id, 8, 6), CountLine(flour.variants[0].id, 5_000, 5_000), CountLine(9_999, 1, 1)),
        )
        val rows = StockCounts.rows(count, products)
        assertEquals(listOf(shirt.variants[0].id, flour.variants[0].id), rows.map { it.variant.id })
    }

    @Test
    fun summary_countsShortagesAndExtras_andValuesThemAtCost() {
        val rows = StockCounts.rows(
            StockCount(
                1, 0,
                listOf(
                    CountLine(shirt.variants[0].id, expected = 8, counted = 6), // 2 short x 250.00
                    CountLine(shirt.variants[1].id, expected = 1, counted = 2), // 1 extra x 250.00
                    CountLine(flour.variants[0].id, expected = 5_000, counted = 5_000), // matches
                ),
            ),
            products,
        )
        val summary = StockCounts.summary(rows)
        assertEquals(3, summary.counted)
        assertEquals(2, summary.withDifference)
        assertEquals(1, summary.short)
        assertEquals(1, summary.extra)
        assertEquals(Money(500_00), summary.shortageValue)
        assertEquals(Money(250_00), summary.extraValue)
        assertEquals(listOf(shirt.variants[0].id, shirt.variants[1].id), StockCounts.differences(rows).map { it.variant.id })
    }

    @Test
    fun weighedItems_areValuedPerKilogram() {
        val row = StockCounts.rows(StockCount(1, 0, listOf(CountLine(flour.variants[0].id, expected = 5_000, counted = 4_000))), products).single()
        assertEquals(Money(-45_00), row.valueAtCost) // 1 kg short at 45.00 per kg
    }

    @Test
    fun anItemWithoutACostPrice_isLeftOutOfTheValues_butStillCounted() {
        val noCost = shirt.copy(variants = shirt.variants.map { it.copy(costPrice = null) })
        val rows = StockCounts.rows(StockCount(1, 0, listOf(CountLine(noCost.variants[0].id, 8, 5))), listOf(noCost))
        val summary = StockCounts.summary(rows)
        assertEquals(1, summary.short)
        assertEquals(Money.ZERO, summary.shortageValue)
        assertNull(rows.single().valueAtCost)
    }

    @Test
    fun countingAnItemAgain_replacesItsLine() {
        val first = listOf(CountLine(1, 8, 5), CountLine(2, 3, 3))
        val again = StockCounts.withLine(first, CountLine(1, 7, 9))
        assertEquals(2, again.size)
        assertEquals(CountLine(1, 7, 9), again.first { it.variantId == 1L })
    }

    @Test
    fun theMovementNote_namesTheReason_andKeepsAShortNote() {
        assertEquals("Stock count · Routine count", StockCounts.movementNote(CountReason.ROUTINE, "  "))
        assertEquals("Stock count · Year-end count · Godown 2", StockCounts.movementNote(CountReason.YEAR_END, " Godown 2 "))
        assertTrue(StockCounts.movementNote(CountReason.OTHER, "x".repeat(500)).length <= "Stock count · Other · ".length + StockCounts.NOTE_MAX)
    }

    @Test
    fun requestIds_areDistinctPerCountAndItem() {
        assertEquals(setOf("stock-count-1-10", "stock-count-1-11", "stock-count-2-10"), setOf(StockCounts.requestId(1, 10), StockCounts.requestId(1, 11), StockCounts.requestId(2, 10)))
    }

    @Test
    fun lines_surviveBeingSavedAsText() {
        val lines = listOf(CountLine(10, 8, 5), CountLine(11, 0, 4))
        assertEquals(lines, StockCounts.decode(StockCounts.encode(lines)))
        assertEquals(emptyList<CountLine>(), StockCounts.decode(StockCounts.encode(emptyList())))
        assertEquals(emptyList<CountLine>(), StockCounts.decode("not json"))
    }
}
