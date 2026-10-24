// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.repository

import androidx.room.withTransaction
import com.auxo.pos.data.local.AuxoDatabase
import com.auxo.pos.data.local.entity.DayClosingEntity
import com.auxo.pos.domain.dayend.DayClosing
import com.auxo.pos.domain.dayend.DayCounts
import com.auxo.pos.domain.dayend.DayFigures
import com.auxo.pos.domain.dayend.DayFiguresBuilder
import com.auxo.pos.domain.dayend.MethodTotal
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.model.PaymentKind
import com.auxo.pos.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

sealed interface CloseDayResult {
    data class Closed(val closing: DayClosing) : CloseDayResult

    /** Nothing was saved: that day was closed before. [closing] is the one on record. */
    data class AlreadyClosed(val closing: DayClosing) : CloseDayResult

    /** Nothing was saved: a day cannot be closed before it has happened. */
    data object FutureDay : CloseDayResult
}

/**
 * Day-end closing. A closing is added once per day and then only read: there is no way to change or
 * delete one, so a closed day stays as it was recorded.
 */
interface DayEndRepository {
    /** The day's figures worked out from the saved bills, credit notes and account receipts, as they are now. */
    suspend fun figuresFor(date: LocalDate): DayFigures

    suspend fun closingFor(date: LocalDate): DayClosing?

    suspend fun getClosing(id: Long): DayClosing?

    /** Closed days, latest first. */
    fun observeClosings(): Flow<List<DayClosing>>

    /** Closes [date] with the cash [counted]. The figures are taken in the same transaction that saves the closing. */
    suspend fun closeDay(date: LocalDate, counted: Money, note: String?, closedBy: String): CloseDayResult

    /** Today's date in the shop's time zone. */
    fun today(): LocalDate
}

class RoomDayEndRepository(
    private val database: AuxoDatabase,
    private val clock: Clock,
) : DayEndRepository {

    private val dao = database.dayEndDao()

    override fun today(): LocalDate = LocalDate.now(clock)

    private fun startOf(date: LocalDate): Long = date.atStartOfDay(clock.zone).toInstant().toEpochMilli()

    private fun endOf(date: LocalDate): Long = startOf(date.plusDays(1))

    override suspend fun figuresFor(date: LocalDate): DayFigures {
        val from = startOf(date)
        val to = endOf(date)
        val counts = dao.counts(from, to)
        val payments = dao.paymentTotals(from, to).mapNotNull { row ->
            val method = runCatching { PaymentMethod.valueOf(row.method) }.getOrNull() ?: return@mapNotNull null
            val kind = runCatching { PaymentKind.valueOf(row.kind) }.getOrNull() ?: return@mapNotNull null
            MethodTotal(method, kind, Money(row.totalMinor))
        }
        val receipts = dao.accountReceiptTotals(from, to).mapNotNull { row ->
            val method = row.method?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            method to Money(row.totalMinor)
        }.groupBy({ it.first }, { it.second }).mapValues { (_, amounts) -> amounts.fold(Money.ZERO) { acc, m -> acc + m } }
        return DayFiguresBuilder.build(date, DayCounts(counts.bills, counts.returns, Money(counts.netMinor)), payments, receipts)
    }

    override suspend fun closingFor(date: LocalDate): DayClosing? = dao.forDate(date.toString())?.let { toDomain(it) }

    override suspend fun getClosing(id: Long): DayClosing? = dao.byId(id)?.let { toDomain(it) }

    override fun observeClosings(): Flow<List<DayClosing>> = dao.observeAll().map { rows -> rows.map { toDomain(it, withLater = false) } }

    override suspend fun closeDay(date: LocalDate, counted: Money, note: String?, closedBy: String): CloseDayResult {
        require(counted >= Money.ZERO) { "Counted cash cannot be negative" }
        if (date.isAfter(today())) return CloseDayResult.FutureDay
        return database.withTransaction {
            dao.forDate(date.toString())?.let { return@withTransaction CloseDayResult.AlreadyClosed(toDomain(it)) }
            val figures = figuresFor(date)
            val entity = DayClosingEntity(
                businessDate = date.toString(),
                bills = figures.bills,
                returns = figures.returns,
                netSalesMinor = figures.netSales.minorUnits,
                cashReceivedMinor = figures.cashReceived.minorUnits,
                cashRefundedMinor = figures.cashRefunded.minorUnits,
                cashFromAccountsMinor = figures.cashFromAccounts.minorUnits,
                upiNetMinor = figures.upiNet.minorUnits,
                cardNetMinor = figures.cardNet.minorUnits,
                onAccountNetMinor = figures.onAccountNet.minorUnits,
                countedCashMinor = counted.minorUnits,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                closedAt = clock.millis(),
                closedBy = closedBy,
            )
            val id = dao.insert(entity)
            if (id == -1L) {
                CloseDayResult.AlreadyClosed(toDomain(requireNotNull(dao.forDate(date.toString()))))
            } else {
                CloseDayResult.Closed(toDomain(entity.copy(id = id)))
            }
        }
    }

    private suspend fun toDomain(row: DayClosingEntity, withLater: Boolean = true): DayClosing {
        val date = LocalDate.parse(row.businessDate)
        val later = if (withLater) {
            val counts = dao.counts(row.closedAt, endOf(date))
            counts.bills + counts.returns
        } else {
            0
        }
        return DayClosing(
            id = row.id,
            figures = DayFigures(
                date = date,
                bills = row.bills,
                returns = row.returns,
                netSales = Money(row.netSalesMinor),
                cashReceived = Money(row.cashReceivedMinor),
                cashRefunded = Money(row.cashRefundedMinor),
                cashFromAccounts = Money(row.cashFromAccountsMinor),
                upiNet = Money(row.upiNetMinor),
                cardNet = Money(row.cardNetMinor),
                onAccountNet = Money(row.onAccountNetMinor),
            ),
            counted = Money(row.countedCashMinor),
            note = row.note,
            closedAtMillis = row.closedAt,
            closedBy = row.closedBy,
            billsAfterClosing = later,
        )
    }
}
