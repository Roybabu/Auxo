// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.customers

import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerPointsTest {

    private fun entry(id: Long, type: LedgerEntryType, amount: Long = 0, points: Int = 0) =
        LedgerEntry(id = id, customerId = 1, type = type, amount = Money(amount), points = points, createdAtMillis = id * 10)

    private val entries = listOf(
        entry(1, LedgerEntryType.SALE, 50_000),
        entry(2, LedgerEntryType.POINTS_EARNED, points = 5),
        entry(3, LedgerEntryType.PAYMENT, -20_000),
        entry(4, LedgerEntryType.POINTS_REDEEMED, points = -3),
    )

    @Test
    fun points_neverChangeWhatTheCustomerOwes() {
        assertEquals(Money(30_000), Ledger.balance(entries))
        assertEquals(Money(30_000), Ledger.balance(entries.filterNot { it.type.isPoints }))
    }

    @Test
    fun theMoneyStatement_leavesPointsOut_andKeepsTheRunningBalance() {
        val statement = Ledger.statement(entries)
        assertEquals(listOf(LedgerEntryType.SALE, LedgerEntryType.PAYMENT), statement.map { it.entry.type })
        assertEquals(listOf(Money(50_000), Money(30_000)), statement.map { it.balanceAfter })
    }

    @Test
    fun theTotals_ignorePoints() {
        assertEquals(Money(50_000) to Money(20_000), Ledger.totals(entries))
    }

    @Test
    fun pointsHistory_isNewestFirst_andOnlyPoints() {
        val history = Ledger.pointsHistory(entries)
        assertEquals(listOf(4L, 2L), history.map { it.id })
        assertTrue(history.all { it.type.isPoints })
    }
}
