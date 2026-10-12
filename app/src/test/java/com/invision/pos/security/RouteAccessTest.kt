package com.invision.pos.security

import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.Role
import com.invision.pos.core.auth.PermissionOptions
import com.invision.pos.core.auth.RolePermissions
import com.invision.pos.ui.navigation.BillingRoutes
import com.invision.pos.ui.navigation.CustomerRoutes
import com.invision.pos.ui.navigation.InventoryRoutes
import com.invision.pos.ui.navigation.MoreRoutes
import com.invision.pos.ui.navigation.RouteAccess
import com.invision.pos.ui.navigation.StockRoutes
import com.invision.pos.ui.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAccessTest {

    private fun allowed(role: Role, route: String): Boolean = RouteAccess.permissionFor(route)?.let { RolePermissions.allows(role, it) } ?: true

    private val everyPage = listOf(
        BillingRoutes.POS, BillingRoutes.INVOICE, BillingRoutes.HISTORY, BillingRoutes.RETURN,
        InventoryRoutes.LIST, InventoryRoutes.PRODUCT, InventoryRoutes.EDITOR, InventoryRoutes.LABELS,
        CustomerRoutes.LIST, CustomerRoutes.DETAIL, CustomerRoutes.EDITOR, CustomerRoutes.PAYMENT,
        StockRoutes.SUPPLIERS, StockRoutes.SUPPLIER_EDITOR, StockRoutes.PURCHASES, StockRoutes.ORDER, StockRoutes.ORDER_EDITOR,
        StockRoutes.RECEIVE, StockRoutes.RECEIPT, StockRoutes.MOVEMENTS, StockRoutes.ALERTS,
        MoreRoutes.HOME, MoreRoutes.MENU, MoreRoutes.MENU_EDITOR, MoreRoutes.REPORTS, MoreRoutes.BACKUP, MoreRoutes.SHOP_SETTINGS,
        MoreRoutes.STORE_DETAILS, MoreRoutes.APPEARANCE, MoreRoutes.USER_MANAGEMENT, MoreRoutes.ACTIVITY_LOG,
        MoreRoutes.DAY_END, MoreRoutes.DAY_CLOSING, MoreRoutes.GST_REPORT, MoreRoutes.RECOVERY_CODE, MoreRoutes.QUICK_UNLOCK, TopLevelDestination.HOME.route,
    )

    @Test
    fun everyPageHasAnExplicitAccessRule() {
        assertEquals(everyPage.toSet(), RouteAccess.registered)
    }

    @Test
    fun theSuperAdmin_mayOpenEveryPage() {
        assertTrue(everyPage.all { allowed(Role.SUPER_ADMIN, it) })
    }

    @Test
    fun anEmployee_mayOpenOnlyBillingInvoicesHomeMoreAndAppearance() {
        val open = everyPage.filter { allowed(Role.EMPLOYEE, it) }.toSet()
        assertEquals(
            setOf(BillingRoutes.POS, BillingRoutes.INVOICE, BillingRoutes.HISTORY, MoreRoutes.HOME, MoreRoutes.APPEARANCE, MoreRoutes.QUICK_UNLOCK, TopLevelDestination.HOME.route),
            open,
        )
    }

    @Test
    fun onlyTheSuperAdmin_mayOpenUserManagement_andTheActivityLog() {
        assertFalse(allowed(Role.EMPLOYEE, MoreRoutes.USER_MANAGEMENT))
        assertFalse(allowed(Role.EMPLOYEE, MoreRoutes.ACTIVITY_LOG))
        assertEquals(Permission.MANAGE_USERS, RouteAccess.permissionFor(MoreRoutes.USER_MANAGEMENT))
    }

    @Test
    fun theGstReport_isSuperAdminOnly() {
        assertEquals(Permission.GST_REPORT, RouteAccess.permissionFor(MoreRoutes.GST_REPORT))
        assertTrue(allowed(Role.SUPER_ADMIN, MoreRoutes.GST_REPORT))
        assertFalse(allowed(Role.EMPLOYEE, MoreRoutes.GST_REPORT))
        assertFalse(openWith(*PermissionOptions.all.toTypedArray()).contains(MoreRoutes.GST_REPORT))
    }

    @Test
    fun quickUnlock_isOpenToEveryoneSignedIn() {
        assertNull(RouteAccess.permissionFor(MoreRoutes.QUICK_UNLOCK))
        assertTrue(allowed(Role.EMPLOYEE, MoreRoutes.QUICK_UNLOCK))
    }

    @Test
    fun theRecoveryCodePage_isSuperAdminOnly() {
        assertEquals(Permission.MANAGE_USERS, RouteAccess.permissionFor(MoreRoutes.RECOVERY_CODE))
        assertTrue(allowed(Role.SUPER_ADMIN, MoreRoutes.RECOVERY_CODE))
        assertFalse(allowed(Role.EMPLOYEE, MoreRoutes.RECOVERY_CODE))
        assertFalse(openWith(*PermissionOptions.all.toTypedArray()).contains(MoreRoutes.RECOVERY_CODE))
    }

    @Test
    fun aPageNobodyListed_isSuperAdminOnly() {
        assertEquals(Permission.MANAGE_USERS, RouteAccess.permissionFor("some/new/page"))
        assertFalse(allowed(Role.EMPLOYEE, "some/new/page"))
    }

    @Test
    fun anEmployee_seesOnlyTheBillingMoreAndHomeTabs() {
        val tabs = TopLevelDestination.entries.filter { it.permission == null || RolePermissions.allows(Role.EMPLOYEE, it.permission) }
        assertEquals(listOf(TopLevelDestination.HOME, TopLevelDestination.BILLING, TopLevelDestination.MORE), tabs)
        assertNull(TopLevelDestination.HOME.permission)
    }

    // Employees with extras

    private fun openWith(vararg extras: Permission): Set<String> =
        everyPage.filter { page -> RouteAccess.permissionFor(page)?.let { RolePermissions.allows(Role.EMPLOYEE, it, extras.toSet()) } ?: true }.toSet()

    private val basics = setOf(BillingRoutes.POS, BillingRoutes.INVOICE, BillingRoutes.HISTORY, MoreRoutes.HOME, MoreRoutes.APPEARANCE, MoreRoutes.QUICK_UNLOCK, TopLevelDestination.HOME.route)

    @Test
    fun givingEveryOption_stillLeavesUserManagementAndBackupToTheSuperAdmin() {
        val open = openWith(*PermissionOptions.all.toTypedArray())
        assertEquals(everyPage.toSet() - MoreRoutes.USER_MANAGEMENT - MoreRoutes.RECOVERY_CODE - MoreRoutes.BACKUP - MoreRoutes.GST_REPORT, open)
    }

    @Test
    fun oneOption_opensOnlyItsOwnPages() {
        assertEquals(basics + MoreRoutes.MENU + MoreRoutes.MENU_EDITOR, openWith(Permission.MANAGE_MENU))
        assertEquals(basics + BillingRoutes.RETURN, openWith(Permission.PROCESS_RETURN))
        assertEquals(basics + MoreRoutes.DAY_END + MoreRoutes.DAY_CLOSING, openWith(Permission.CLOSE_DAY))
        assertEquals(basics + MoreRoutes.SHOP_SETTINGS + MoreRoutes.STORE_DETAILS, openWith(Permission.MANAGE_SETTINGS))
        assertEquals(basics + MoreRoutes.ACTIVITY_LOG, openWith(Permission.VIEW_ACTIVITY_LOG))
    }

    @Test
    fun everyPageAnOptionOpens_canBeReachedFromItsEntryPage() {
        // Receiving goods starts from an order, which needs suppliers; account payments start from a customer.
        val goods = openWith(Permission.RECEIVE_GOODS)
        assertTrue(StockRoutes.RECEIVE in goods && StockRoutes.ORDER in goods && StockRoutes.SUPPLIERS in goods)
        val payments = openWith(Permission.RECEIVE_ACCOUNT_PAYMENT)
        assertTrue(CustomerRoutes.PAYMENT in payments && CustomerRoutes.LIST in payments && CustomerRoutes.DETAIL in payments)
        val adjust = openWith(Permission.ADJUST_STOCK)
        assertTrue(InventoryRoutes.PRODUCT in adjust)
    }

    @Test
    fun theTabBar_followsWhatTheEmployeeWasGiven() {
        fun tabs(vararg extras: Permission) =
            TopLevelDestination.entries.filter { it.permission == null || RolePermissions.allows(Role.EMPLOYEE, it.permission, extras.toSet()) }
        assertEquals(
            listOf(TopLevelDestination.HOME, TopLevelDestination.BILLING, TopLevelDestination.INVENTORY, TopLevelDestination.MORE),
            tabs(Permission.MANAGE_PRODUCTS),
        )
        assertEquals(
            listOf(TopLevelDestination.HOME, TopLevelDestination.BILLING, TopLevelDestination.CUSTOMERS, TopLevelDestination.MORE),
            tabs(Permission.MANAGE_CUSTOMERS),
        )
    }
}
