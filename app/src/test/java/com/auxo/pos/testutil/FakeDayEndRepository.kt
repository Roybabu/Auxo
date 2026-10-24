// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.testutil

import com.auxo.pos.data.repository.CloseDayResult
import com.auxo.pos.data.repository.DayEndRepository
import com.auxo.pos.domain.dayend.DayClosing
import com.auxo.pos.domain.dayend.DayFigures
import com.auxo.pos.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** In-memory day-end records for view model and screen tests. [figures] is what the day currently adds up to. */
class FakeDayEndRepository(
    private val today: LocalDate = LocalDate.of(2026, 10, 12),
    var figures: DayFigures = sampleFigures(LocalDate.of(2026, 10, 12)),
    initial: List<DayClosing> = emptyList(),
) : DayEndRepository {

    val closings = MutableStateFlow(initial)
    var failWith: Exception? = null
    var closeCalls = 0
    var lastNote: String? = null

    override fun today(): LocalDate = today

    override suspend fun figuresFor(date: LocalDate): DayFigures {
        failWith?.let { throw it }
        return figures.copy(date = date)
    }

    override suspend fun closingFor(date: LocalDate): DayClosing? = closings.value.firstOrNull { it.date == date }

    override suspend fun getClosing(id: Long): DayClosing? {
        failWith?.let { throw it }
        return closings.value.firstOrNull { it.id == id }
    }

    override fun observeClosings(): Flow<List<DayClosing>> = closings

    override suspend fun closeDay(date: LocalDate, counted: Money, note: String?, closedBy: String): CloseDayResult {
        failWith?.let { throw it }
        closeCalls++
        lastNote = note
        closingFor(date)?.let { return CloseDayResult.AlreadyClosed(it) }
        if (date.isAfter(today)) return CloseDayResult.FutureDay
        val closing = DayClosing(
            id = closings.value.size + 1L,
            figures = figures.copy(date = date),
            counted = counted,
            note = note?.trim()?.ifEmpty { null },
            closedAtMillis = 1_000L,
            closedBy = closedBy.ifEmpty { "Test Admin" },
        )
        closings.value = (listOf(closing) + closings.value).sortedByDescending { it.date }
        return CloseDayResult.Closed(closing)
    }

    companion object {
        /** Expected cash comes to ₹1,250.00. */
        fun sampleFigures(date: LocalDate) = DayFigures(
            date = date,
            bills = 5,
            returns = 1,
            netSales = Money(2_000_00),
            cashReceived = Money(1_000_00),
            cashRefunded = Money(150_00),
            cashFromAccounts = Money(400_00),
            upiNet = Money(700_00),
            cardNet = Money(200_00),
            onAccountNet = Money(200_00),
        )
    }
}
