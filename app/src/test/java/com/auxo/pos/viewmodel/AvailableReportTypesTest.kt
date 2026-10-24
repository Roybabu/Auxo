// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.viewmodel

import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.Role
import com.auxo.pos.core.auth.RolePermissions
import com.auxo.pos.domain.reports.ReportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailableReportTypesTest {

    private fun typesFor(granted: Set<Permission>, suppliersOn: Boolean = true): List<ReportType> {
        val held = RolePermissions.of(Role.EMPLOYEE, granted)
        return availableReportTypes(suppliersOn) { it in held }
    }

    @Test
    fun theSuperAdmin_seesEveryReport() {
        val all = RolePermissions.of(Role.SUPER_ADMIN)
        assertEquals(ReportType.entries, availableReportTypes(true) { it in all })
    }

    @Test
    fun anEmployeeWithOnlyReports_doesNotSeeThePurchaseOrStockRecordReports() {
        val types = typesFor(setOf(Permission.VIEW_REPORTS))
        listOf(ReportType.PURCHASES, ReportType.STOCK_MOVEMENTS, ReportType.WASTAGE, ReportType.EXPIRY).forEach {
            assertFalse("$it", it in types)
        }
        // The rest read only sales, stock levels and customer accounts, which every Employee may read.
        assertTrue(ReportType.SALES_SUMMARY in types && ReportType.LOW_STOCK in types && ReportType.CUSTOMER_BALANCES in types)
    }

    @Test
    fun eachExtraPermission_bringsBackItsReports() {
        assertTrue(ReportType.EXPIRY in typesFor(setOf(Permission.VIEW_REPORTS, Permission.VIEW_STOCK_RECORDS)))
        assertTrue(ReportType.WASTAGE in typesFor(setOf(Permission.VIEW_REPORTS, Permission.VIEW_STOCK_RECORDS)))
        assertTrue(ReportType.PURCHASES in typesFor(setOf(Permission.VIEW_REPORTS, Permission.MANAGE_PURCHASES)))
    }

    @Test
    fun withSuppliersTurnedOff_thePurchaseReportIsHiddenForEveryone() {
        val all = RolePermissions.of(Role.SUPER_ADMIN)
        assertFalse(ReportType.PURCHASES in availableReportTypes(false) { it in all })
    }

    @Test
    fun everyReportThatNeedsAPermission_isOneAnEmployeeCanBeGiven() {
        ReportType.entries.mapNotNull { it.requiredPermission }.forEach {
            assertTrue("$it", com.auxo.pos.core.auth.PermissionOptions.isGrantable(it))
        }
    }
}
