package com.invision.pos.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionOptionsTest {

    @Test
    fun theOptions_neverIncludeTheBasicsOrWhatOnlyTheSuperAdminHolds() {
        assertFalse(Permission.SELL in PermissionOptions.all)
        assertFalse(Permission.VIEW_INVOICES in PermissionOptions.all)
        assertFalse(Permission.MANAGE_USERS in PermissionOptions.all)
        assertFalse(Permission.BACKUP_AND_RESTORE in PermissionOptions.all)
        assertFalse(Permission.GST_REPORT in PermissionOptions.all)
        assertEquals(PermissionOptions.all.size, PermissionOptions.all.toSet().size)
    }

    @Test
    fun everyPermission_isEitherAnOption_aBasic_orSuperAdminOnly() {
        val accounted = PermissionOptions.all.toSet() + RolePermissions.BASE + setOf(Permission.MANAGE_USERS, Permission.BACKUP_AND_RESTORE, Permission.GST_REPORT)
        assertEquals(Permission.entries.toSet(), accounted)
    }

    @Test
    fun normalize_dropsWhatCanNeverBeGranted() {
        val result = PermissionOptions.normalize(setOf(Permission.MANAGE_USERS, Permission.BACKUP_AND_RESTORE, Permission.MANAGE_MENU))
        assertEquals(setOf(Permission.MANAGE_MENU), result)
    }

    @Test
    fun normalize_addsWhateverAnOptionNeeds() {
        assertEquals(
            setOf(Permission.RECEIVE_GOODS, Permission.MANAGE_PURCHASES, Permission.MANAGE_SUPPLIERS),
            PermissionOptions.normalize(setOf(Permission.RECEIVE_GOODS)),
        )
        assertEquals(
            setOf(Permission.ADJUST_STOCK, Permission.MANAGE_PRODUCTS, Permission.VIEW_STOCK_RECORDS),
            PermissionOptions.normalize(setOf(Permission.ADJUST_STOCK)),
        )
        assertEquals(setOf(Permission.VIEW_DASHBOARD, Permission.VIEW_FINANCIALS), PermissionOptions.normalize(setOf(Permission.VIEW_DASHBOARD)))
    }

    @Test
    fun everyRequirement_isItselfAnOption_andNothingNeedsItself() {
        PermissionOptions.all.forEach { p ->
            val needs = PermissionOptions.requirementsOf(p)
            assertTrue(needs.all { PermissionOptions.isGrantable(it) })
            assertFalse(p in needs)
        }
    }

    @Test
    fun switchingOff_alsoSwitchesOffWhatNeededIt() {
        val all = PermissionOptions.normalize(setOf(Permission.RECEIVE_GOODS))
        assertEquals(setOf(Permission.MANAGE_SUPPLIERS), PermissionOptions.switchOff(all, Permission.MANAGE_PURCHASES))
        assertEquals(emptySet<Permission>(), PermissionOptions.switchOff(all, Permission.MANAGE_SUPPLIERS))
        assertEquals(all - Permission.RECEIVE_GOODS, PermissionOptions.switchOff(all, Permission.RECEIVE_GOODS))
    }

    @Test
    fun switchingOn_addsRequirements() {
        assertEquals(
            setOf(Permission.RECEIVE_ACCOUNT_PAYMENT, Permission.MANAGE_CUSTOMERS),
            PermissionOptions.switchOn(emptySet(), Permission.RECEIVE_ACCOUNT_PAYMENT),
        )
    }

    @Test
    fun anEmployee_holdsTheBasicsPlusWhatWasGranted_andNeverUserManagement() {
        val granted = setOf(Permission.MANAGE_MENU, Permission.MANAGE_USERS)
        val held = RolePermissions.of(Role.EMPLOYEE, granted)
        assertEquals(RolePermissions.BASE + Permission.MANAGE_MENU, held)
        assertFalse(RolePermissions.allows(Role.EMPLOYEE, Permission.MANAGE_USERS, granted))
    }

    @Test
    fun theSuperAdmin_holdsEverything_whateverIsGranted() {
        assertEquals(Permission.entries.toSet(), RolePermissions.of(Role.SUPER_ADMIN, emptySet()))
    }
}
