package com.invision.pos.security

import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.Role
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
        TopLevelDestination.HOME.route,
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
            setOf(BillingRoutes.POS, BillingRoutes.INVOICE, BillingRoutes.HISTORY, MoreRoutes.HOME, MoreRoutes.APPEARANCE, TopLevelDestination.HOME.route),
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
}
