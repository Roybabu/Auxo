// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import com.auxo.pos.domain.model.Money

/** One parked bill in the list: enough to recognise it and decide whether to resume it. */
data class HeldBillSummary(
    val id: Long,
    /** What the cashier called it (a customer name, a table); empty when they did not. */
    val label: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val itemCount: Int,
    /** The total when it was parked. Prices and stock are checked again when it is resumed. */
    val total: Money,
    /** The restaurant table the bill is parked against; null for bills that are not for a table. */
    val tableNumber: Int? = null,
    /** The number the app gave a bill that is not for a table (1, 2, 3 ...); null for table bills. */
    val tokenNumber: Int? = null,
)

/** A parked bill with everything needed to put it back on the counter. */
data class HeldBill(val summary: HeldBillSummary, val cart: Cart)

object HeldBills {
    const val LABEL_MAX = 40

    /** Tables a restaurant starts with, and the most the owner can set. */
    const val DEFAULT_TABLES = 10
    const val MAX_TABLES = 99

    /** The last 10 digits of a typed or saved phone number ("+91 98300 12345" becomes "9830012345"), or "" when it has none. */
    fun phoneDigits(raw: String): String = raw.filter(Char::isDigit).takeLast(10)

    /** The smallest number from 1 that no parked bill is using, so a number freed by a finished bill is used again. */
    fun nextToken(used: Collection<Int>): Int = generateSequence(1) { it + 1 }.first { it !in used }

    /** Tables that already have a parked bill, except the one being resumed (it may be parked there again). */
    fun busyTables(bills: List<HeldBillSummary>, exceptId: Long? = null): Set<Int> =
        bills.filter { it.id != exceptId }.mapNotNull { it.tableNumber }.toSet()

    /** A table count as the owner typed it, or null when it is not a number from 1 to [MAX_TABLES]. */
    fun parseTableCount(text: String): Int? = text.trim().takeIf { it.length in 1..2 && it.all(Char::isDigit) }?.toInt()?.takeIf { it in 1..MAX_TABLES }

    /** A parked bill that has waited this long is flagged, so a customer's bill is not forgotten. */
    const val STALE_HOURS = 2

    /** Whole minutes a bill has been parked (since it was last saved), never negative. */
    fun waitingMinutes(bill: HeldBillSummary, nowMillis: Long): Long = ((nowMillis - bill.updatedAtMillis) / 60_000L).coerceAtLeast(0)

    /** True when [bill] has waited at least [STALE_HOURS] hours. */
    fun isStale(bill: HeldBillSummary, nowMillis: Long): Boolean = waitingMinutes(bill, nowMillis) >= STALE_HOURS * 60L

    /** How many of [bills] are stale. */
    fun staleCount(bills: List<HeldBillSummary>, nowMillis: Long): Int = bills.count { isStale(it, nowMillis) }

    /** The label as it is kept: one line, trimmed, not longer than [LABEL_MAX]. */
    fun cleanLabel(label: String): String = label.replace(Regex("\\s+"), " ").trim().take(LABEL_MAX)
}
