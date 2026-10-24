// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.billing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BusinessTimeTest {

    @Test
    fun zone_isIndianStandardTime() {
        assertEquals(ZoneId.of("Asia/Kolkata"), BusinessTime.ZONE)
        assertEquals(ZoneId.of("Asia/Kolkata"), BusinessTime.clock().zone)
    }

    @Test
    fun clock_decidesTheCalendarDayInIndia() {
        // 19:00 UTC on 31 March is 00:30 on 1 April in India.
        val fixed = java.time.Clock.fixed(Instant.parse("2027-03-31T19:00:00Z"), BusinessTime.ZONE)
        assertEquals(LocalDate.of(2027, 4, 1), LocalDate.now(fixed))
        assertEquals(2027, InvoiceNumbers.financialYearStart(LocalDate.now(fixed)))
    }

    @Test
    fun financialYear_changesAtIndianMidnight() {
        fun year(iso: String) = InvoiceNumbers.financialYearStart(Instant.parse(iso).atZone(BusinessTime.ZONE).toLocalDate())
        assertEquals(2026, year("2027-03-31T18:29:59Z"))
        assertEquals(2027, year("2027-03-31T18:30:00Z"))
    }
}
