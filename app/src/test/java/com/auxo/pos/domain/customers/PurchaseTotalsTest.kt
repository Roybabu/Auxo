// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.customers

import com.auxo.pos.domain.model.InvoiceSummary
import com.auxo.pos.domain.model.InvoiceType
import com.auxo.pos.domain.model.LedgerEntry
import com.auxo.pos.domain.model.LedgerEntryType
import com.auxo.pos.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseTotalsTest {

    private fun bill(id: Long, type: InvoiceType, total: Long, at: Long) =
        InvoiceSummary(id, "N$id", type, at, "Asha", null, Money(total), 1)

    private fun entry(type: LedgerEntryType, amount: Long) =
        LedgerEntry(customerId = 1, type = type, amount = Money(amount), createdAtMillis = 1)

    @Test
    fun bought_isBillsLessCreditNotes_andPaidIsWhatIsNotStillOwed() {
        val bills = listOf(bill(1, InvoiceType.SALE, 100_000, 10), bill(2, InvoiceType.SALE, 50_000, 20), bill(3, InvoiceType.RETURN, 20_000, 30))
        val ledger = listOf(entry(LedgerEntryType.SALE, 80_000), entry(LedgerEntryType.PAYMENT, -30_000))
        val (bought, paid) = Ledger.purchases(bills, ledger)
        assertEquals(Money(130_000), bought)
        assertEquals(Money(80_000), paid) // 1,300 bought, 500 still owed
    }

    @Test
    fun anOpeningBalance_isNotABill_andIsLeftOut() {
        val bills = listOf(bill(1, InvoiceType.SALE, 100_000, 10))
        val ledger = listOf(entry(LedgerEntryType.OPENING_BALANCE, 70_000), entry(LedgerEntryType.SALE, 40_000))
        val (bought, paid) = Ledger.purchases(bills, ledger)
        assertEquals(Money(100_000), bought)
        assertEquals(Money(60_000), paid)
    }

    @Test
    fun lifetime_isTheLastSaleAndTheAverageBill_ignoringCreditNotes() {
        val bills = listOf(bill(1, InvoiceType.SALE, 100_000, 10), bill(2, InvoiceType.SALE, 50_000, 20), bill(3, InvoiceType.RETURN, 20_000, 30))
        val lifetime = Ledger.lifetime(bills)
        assertEquals(20L, lifetime.lastPurchaseMillis)
        assertEquals(Money(75_000), lifetime.averageBill)
        assertNull(Ledger.lifetime(emptyList()).lastPurchaseMillis)
    }

    @Test
    fun aging_putsPaymentsAgainstTheOldestChargeFirst() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        fun at(type: LedgerEntryType, amount: Long, daysAgo: Long) =
            LedgerEntry(customerId = 1, type = type, amount = Money(amount), createdAtMillis = now - daysAgo * day)
        val entries = listOf(
            at(LedgerEntryType.SALE, 100_00, 40),
            at(LedgerEntryType.SALE, 50_00, 10),
            at(LedgerEntryType.SALE, 20_00, 2),
            at(LedgerEntryType.PAYMENT, -60_00, 1),
        )
        val buckets = Ledger.aging(entries, now, java.time.ZoneOffset.UTC)
        assertEquals(Money(20_00), buckets.upTo7Days)
        assertEquals(Money(50_00), buckets.upTo30Days)
        assertEquals(Money(40_00), buckets.over30Days)
        assertEquals(Money(110_00), buckets.total)
    }

    @Test
    fun owedByInvoice_settlesTheOldestBillFirst_andBillStatusFollows() {
        fun sale(invoiceId: Long, amount: Long, at: Long) =
            LedgerEntry(customerId = 1, type = LedgerEntryType.SALE, amount = Money(amount), invoiceId = invoiceId, createdAtMillis = at)
        val entries = listOf(
            sale(1, 100_00, 1),
            sale(2, 50_00, 2),
            LedgerEntry(customerId = 1, type = LedgerEntryType.PAYMENT, amount = Money(-120_00), createdAtMillis = 3),
        )
        val owed = Ledger.owedByInvoice(entries)
        assertNull(owed[1L]) // paid in full
        assertEquals(Money(30_00), owed[2L]) // 20 of 50 paid
        assertEquals(BillStatus.PAID, Ledger.billStatus(InvoiceType.SALE, Money(100_00), Money.ZERO))
        assertEquals(BillStatus.PART_PAID, Ledger.billStatus(InvoiceType.SALE, Money(100_00), Money(30_00)))
        assertEquals(BillStatus.UNPAID, Ledger.billStatus(InvoiceType.SALE, Money(100_00), Money(100_00)))
        assertEquals(BillStatus.RETURN, Ledger.billStatus(InvoiceType.RETURN, Money(100_00), Money.ZERO))
    }
}
