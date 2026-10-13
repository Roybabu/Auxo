package com.auxo.pos.security

import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.auth.Role
import com.auxo.pos.core.auth.PermissionOptions
import com.auxo.pos.core.auth.RolePermissions
import com.auxo.pos.ui.navigation.BillingRoutes
import com.auxo.pos.ui.navigation.CustomerRoutes
import com.auxo.pos.ui.navigation.InventoryRoutes
import com.auxo.pos.ui.navigation.MoreRoutes
import com.auxo.pos.ui.navigation.RouteAccess
import com.auxo.pos.ui.navigation.StockRoutes
import com.auxo.pos.ui.navigation.TopLevelDestination
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
        CustomerRoutes.LIST, CustomerRoutes.DETAIL, CustomerRoutes.EDITOR, CustomerRoutes.PAYMENT, CustomerRoutes.HISTORY,
        StockRoutes.SUPPLIERS, StockRoutes.SUPPLIER_EDITOR, StockRoutes.PURCHASES, StockRoutes.ORDER, StockRoutes.ORDER_EDITOR,
        StockRoutes.RECEIVE, StockRoutes.RECEIPT, StockRoutes.MOVEMENTS, StockRoutes.ALERTS, StockRoutes.COUNT, StockRoutes.IMPORT,
        MoreRoutes.HOME, MoreRoutes.REPORTS, MoreRoutes.BACKUP, MoreRoutes.SHOP_SETTINGS, MoreRoutes.PRINTER,
        MoreRoutes.STORE_DETAILS, MoreRoutes.APPEARANCE, MoreRoutes.USER_MANAGEMENT, MoreRoutes.ACTIVITY_LOG,
        MoreRoutes.DAY_END, MoreRoutes.DAY_CLOSING, MoreRoutes.GST_REPORT, MoreRoutes.LOYALTY, MoreRoutes.RECOVERY_CODE, MoreRoutes.QUICK_UNLOCK, TopLevelDestination.HOME.route,
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
    fun theProductImport_needsTheManageProductsPermission() {
        assertEquals(Permission.MANAGE_PRODUCTS, RouteAccess.permissionFor(StockRoutes.IMPORT))
        assertTrue(allowed(Role.SUPER_ADMIN, StockRoutes.IMPORT))
        assertFalse(allowed(Role.EMPLOYEE, StockRoutes.IMPORT))
        assertTrue(openWith(Permission.MANAGE_PRODUCTS).contains(StockRoutes.IMPORT))
    }

    @Test
    fun theStockCount_needsTheAdjustStockPermission() {
        assertEquals(Permission.ADJUST_STOCK, RouteAccess.permissionFor(StockRoutes.COUNT))
        assertTrue(allowed(Role.SUPER_ADMIN, StockRoutes.COUNT))
        assertFalse(allowed(Role.EMPLOYEE, StockRoutes.COUNT))
        assertTrue(openWith(Permission.ADJUST_STOCK).contains(StockRoutes.COUNT))
        assertFalse(openWith(Permission.VIEW_STOCK_RECORDS).contains(StockRoutes.COUNT))
    }

    @Test
    fun theGstReport_isSuperAdminOnly() {
        assertEquals(Permission.GST_REPORT, RouteAccess.permissionFor(MoreRoutes.GST_REPORT))
        assertTrue(allowed(Role.SUPER_ADMIN, MoreRoutes.GST_REPORT))
        assertFalse(allowed(Role.EMPLOYEE, MoreRoutes.GST_REPORT))
        assertFalse(openWith(*PermissionOptions.all.toTypedArray()).contains(MoreRoutes.GST_REPORT))
    }

    @Test
    fun theLoyaltySettings_areSuperAdminOnly() {
        assertEquals(Permission.MANAGE_LOYALTY, RouteAccess.permissionFor(MoreRoutes.LOYALTY))
        assertTrue(allowed(Role.SUPER_ADMIN, MoreRoutes.LOYALTY))
        assertFalse(allowed(Role.EMPLOYEE, MoreRoutes.LOYALTY))
        assertFalse(openWith(*PermissionOptions.all.toTypedArray()).contains(MoreRoutes.LOYALTY))
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
        assertEquals(everyPage.toSet() - MoreRoutes.USER_MANAGEMENT - MoreRoutes.RECOVERY_CODE - MoreRoutes.BACKUP - MoreRoutes.GST_REPORT - MoreRoutes.LOYALTY, open)
    }

    @Test
    fun oneOption_opensOnlyItsOwnPages() {
        assertEquals(basics, openWith(Permission.MANAGE_MENU)) // no page is tied to the menu permission any more
        assertEquals(basics + BillingRoutes.RETURN, openWith(Permission.PROCESS_RETURN))
        assertEquals(basics + MoreRoutes.DAY_END + MoreRoutes.DAY_CLOSING, openWith(Permission.CLOSE_DAY))
        assertEquals(basics + MoreRoutes.SHOP_SETTINGS + MoreRoutes.PRINTER + MoreRoutes.STORE_DETAILS, openWith(Permission.MANAGE_SETTINGS))
        assertEquals(basics + MoreRoutes.ACTIVITY_LOG, openWith(Permission.VIEW_ACTIVITY_LOG))
    }

    @Test
    fun everyPageAnOptionOpens_canBeReachedFromItsEntryPage() {
        // Receiving goods starts from an order, which needs suppliers; account payments start from a customer.
        val goods = openWith(Permission.RECEIVE_GOODS)
        assertTrue(StockRoutes.RECEIVE in goods && StockRoutes.ORDER in goods && StockRoutes.SUPPLIERS in goods)
        val payments = openWith(Permission.RECEIVE_ACCOUNT_PAYMENT)
        assertTrue(CustomerRoutes.PAYMENT in payments && CustomerRoutes.LIST in payments && CustomerRoutes.DETAIL in payments)
        assertEquals(Permission.MANAGE_CUSTOMERS, RouteAccess.permissionFor(CustomerRoutes.HISTORY))
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
