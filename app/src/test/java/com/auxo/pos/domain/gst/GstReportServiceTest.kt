// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.domain.gst

import com.auxo.pos.core.auth.FixedAuthorizer
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.PermissionDeniedException
import com.auxo.pos.core.auth.Role
import com.auxo.pos.data.security.GstReportService
import com.auxo.pos.domain.model.Money
import com.auxo.pos.domain.reports.PeriodPreset
import com.auxo.pos.domain.reports.ReportPeriod
import com.auxo.pos.testutil.FakeBillingRepository
import com.auxo.pos.testutil.FakeInventoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class GstReportServiceTest {

    private val billing = FakeBillingRepository(FakeInventoryRepository(emptyList())).apply { invoices.value = GstTestData.all }

    private fun period(from: LocalDate, to: LocalDate) = ReportPeriod.of(PeriodPreset.CUSTOM, to, from, to)

    @Test
    fun theSuperAdmin_getsTheReportForTheChosenDays() = runTest {
        val service = GstReportService(billing, FixedAuthorizer(Role.SUPER_ADMIN))
        val whole = service.report(period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), GstTestData.zone)
        assertEquals(Money(3_535_00), whole.net.invoiceValue)
        assertEquals(5, whole.sales + whole.creditNotes)

        // 3 to 5 October: invoices A and B only, no credit notes yet.
        val early = service.report(period(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5)), GstTestData.zone)
        assertEquals(2, early.sales)
        assertEquals(0, early.creditNotes)
        assertEquals(Money(1_050_00 + 2_555_00), early.net.invoiceValue)
    }

    @Test
    fun theEndDay_isIncluded_andOtherMonthsAreNot() = runTest {
        val service = GstReportService(billing, FixedAuthorizer(Role.SUPER_ADMIN))
        val lastDay = service.report(period(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14)), GstTestData.zone)
        assertEquals(1, lastDay.creditNotes) // credit note E is on the 14th
        val september = service.report(period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), GstTestData.zone)
        assertEquals(true, september.isEmpty)
    }

    @Test
    fun anEmployee_cannotGetItEvenWithEveryOption() = runTest {
        val everything = com.auxo.pos.core.auth.PermissionOptions.all.toSet()
        val service = GstReportService(billing, FixedAuthorizer(Role.EMPLOYEE, everything))
        try {
            service.report(period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), GstTestData.zone)
            fail("Expected a refusal")
        } catch (e: PermissionDeniedException) {
            assertEquals(Permission.GST_REPORT, e.permission)
        }
    }
}
