// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The store's time zone. Invoice dates, financial-year numbering and day boundaries
 * for reports are all decided here, so a phone set to another zone cannot change
 * which day or financial year a bill falls in.
 */
object BusinessTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun clock(): Clock = Clock.system(ZONE)
}

/**
 * Today's date in this clock's zone, then the new date each time the day changes. "Today" figures that are
 * collected from this move on at midnight, even when the app stays open all night.
 *
 * A clock that never moves (a fixed clock in tests) gives just the one date.
 */
fun Clock.businessDays(): Flow<LocalDate> = flow {
    var day = LocalDate.now(this@businessDays)
    emit(day)
    while (true) {
        val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val before = millis()
        delay((nextMidnight - before).coerceAtLeast(1L))
        if (millis() == before) break
        val now = LocalDate.now(this@businessDays)
        if (now != day) {
            day = now
            emit(day)
        }
    }
}
