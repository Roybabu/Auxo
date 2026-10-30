// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeldBillsTest {

    private fun bill(id: Long, table: Int? = null, token: Int? = null) =
        HeldBillSummary(id, "", 0, 0, 1, Money(100), tableNumber = table, tokenNumber = token)

    @Test
    fun theNextNumber_isTheSmallestOneNotInUse() {
        assertEquals(1, HeldBills.nextToken(emptyList()))
        assertEquals(3, HeldBills.nextToken(listOf(1, 2)))
        assertEquals(1, HeldBills.nextToken(listOf(2, 3)))
        assertEquals(11, HeldBills.nextToken((1..10).toList()))
    }

    @Test
    fun busyTables_leaveOutTheBillBeingResumed() {
        val bills = listOf(bill(1, table = 2), bill(2, table = 5), bill(3, token = 1))
        assertEquals(setOf(2, 5), HeldBills.busyTables(bills))
        assertEquals(setOf(5), HeldBills.busyTables(bills, exceptId = 1))
    }

    @Test
    fun theTableCount_mustBeFromOneToNinetyNine() {
        assertEquals(12, HeldBills.parseTableCount(" 12 "))
        assertEquals(1, HeldBills.parseTableCount("1"))
        assertEquals(99, HeldBills.parseTableCount("99"))
        assertNull(HeldBills.parseTableCount("0"))
        assertNull(HeldBills.parseTableCount("100"))
        assertNull(HeldBills.parseTableCount(""))
        assertNull(HeldBills.parseTableCount("a"))
    }

    private fun parkedAt(id: Long, updated: Long) = HeldBillSummary(id, "", 0, updated, 1, Money(100), tokenNumber = 1)

    @Test
    fun aBill_isStaleAfterTwoHours_andNotBefore() {
        val minute = 60_000L
        val bill = parkedAt(1, updated = 0)
        assertEquals(0, HeldBills.waitingMinutes(bill, 0))
        assertEquals(119, HeldBills.waitingMinutes(bill, 119 * minute + 59_000))
        assertEquals(false, HeldBills.isStale(bill, 119 * minute))
        assertEquals(true, HeldBills.isStale(bill, 120 * minute))
    }

    @Test
    fun theClockBeingBehind_neverGivesNegativeWaiting() {
        assertEquals(0, HeldBills.waitingMinutes(parkedAt(1, updated = 10_000_000), 5_000_000))
    }

    @Test
    fun staleCount_countsOnlyTheOldBills() {
        val hour = 3_600_000L
        val bills = listOf(parkedAt(1, 0), parkedAt(2, 2 * hour), parkedAt(3, 3 * hour))
        assertEquals(1, HeldBills.staleCount(bills, 3 * hour))
        assertEquals(2, HeldBills.staleCount(bills, 4 * hour))
        assertEquals(0, HeldBills.staleCount(emptyList(), 4 * hour))
    }

    @Test
    fun phoneDigits_keepsTheLastTenDigits() {
        assertEquals("9830012345", HeldBills.phoneDigits("98300 12345"))
        assertEquals("9830012345", HeldBills.phoneDigits("+91 98300-12345"))
        assertEquals("", HeldBills.phoneDigits(""))
        assertEquals("", HeldBills.phoneDigits("none"))
    }
}
