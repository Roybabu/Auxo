// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BusinessDaysTest {

    /** A clock that moves with the test's virtual time. */
    private class SchedulerClock(private val scope: TestScope, private val start: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = SchedulerClock(scope, start, zone)
        override fun instant(): Instant = start.plusMillis(scope.testScheduler.currentTime)
    }

    @Test
    fun theDayMovesOnAtMidnight() = runTest {
        val start = LocalDateTime.of(2026, 10, 21, 23, 59).atZone(BusinessTime.ZONE).toInstant()
        val days = SchedulerClock(this, start, BusinessTime.ZONE).businessDays().take(3).toList()
        assertEquals(listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23)), days)
    }

    @Test
    fun aClockThatNeverMoves_givesJustOneDay() = runTest {
        val clock = Clock.fixed(LocalDateTime.of(2026, 10, 21, 10, 0).atZone(BusinessTime.ZONE).toInstant(), BusinessTime.ZONE)
        assertEquals(listOf(LocalDate.of(2026, 10, 21)), clock.businessDays().toList())
    }
}
