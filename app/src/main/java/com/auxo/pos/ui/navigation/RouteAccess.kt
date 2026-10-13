package com.auxo.pos.ui.navigation

import com.auxo.pos.core.auth.Permission

/**
 * What each page requires. Every page the app registers is listed here, so a page added later without an
 * entry is caught by a test instead of being open to everyone by accident. The navigation host checks this
 * before it builds a page, which blocks pages reached by Home shortcuts, in-app links or any other route.
 */
object RouteAccess {

    /** Pages every signed-in person may open. */
    private val OPEN = setOf(
        TopLevelDestination.HOME.route,
        MoreRoutes.HOME,
        MoreRoutes.APPEARANCE,
        MoreRoutes.QUICK_UNLOCK,
    )

    private val REQUIRED: Map<String, Permission> = mapOf(
        BillingRoutes.POS to Permission.SELL,
        BillingRoutes.INVOICE to Permission.VIEW_INVOICES,
        BillingRoutes.HISTORY to Permission.VIEW_INVOICES,
        BillingRoutes.RETURN to Permission.PROCESS_RETURN,

        InventoryRoutes.LIST to Permission.MANAGE_PRODUCTS,
        InventoryRoutes.PRODUCT to Permission.MANAGE_PRODUCTS,
        InventoryRoutes.EDITOR to Permission.MANAGE_PRODUCTS,
        InventoryRoutes.LABELS to Permission.MANAGE_PRODUCTS,

        CustomerRoutes.LIST to Permission.MANAGE_CUSTOMERS,
        CustomerRoutes.DETAIL to Permission.MANAGE_CUSTOMERS,
        CustomerRoutes.HISTORY to Permission.MANAGE_CUSTOMERS,
        CustomerRoutes.EDITOR to Permission.MANAGE_CUSTOMERS,
        CustomerRoutes.PAYMENT to Permission.RECEIVE_ACCOUNT_PAYMENT,

        StockRoutes.SUPPLIERS to Permission.MANAGE_SUPPLIERS,
        StockRoutes.SUPPLIER_EDITOR to Permission.MANAGE_SUPPLIERS,
        StockRoutes.PURCHASES to Permission.MANAGE_PURCHASES,
        StockRoutes.ORDER to Permission.MANAGE_PURCHASES,
        StockRoutes.ORDER_EDITOR to Permission.MANAGE_PURCHASES,
        StockRoutes.RECEIPT to Permission.MANAGE_PURCHASES,
        StockRoutes.RECEIVE to Permission.RECEIVE_GOODS,
        StockRoutes.MOVEMENTS to Permission.VIEW_STOCK_RECORDS,
        StockRoutes.ALERTS to Permission.VIEW_STOCK_RECORDS,
        StockRoutes.COUNT to Permission.ADJUST_STOCK,
        StockRoutes.IMPORT to Permission.MANAGE_PRODUCTS,

        MoreRoutes.REPORTS to Permission.VIEW_REPORTS,
        MoreRoutes.BACKUP to Permission.BACKUP_AND_RESTORE,
        MoreRoutes.SHOP_SETTINGS to Permission.MANAGE_SETTINGS,
        MoreRoutes.PRINTER to Permission.MANAGE_SETTINGS,
        MoreRoutes.STORE_DETAILS to Permission.MANAGE_SETTINGS,
        MoreRoutes.CATEGORIES to Permission.MANAGE_SETTINGS,
        MoreRoutes.USER_MANAGEMENT to Permission.MANAGE_USERS,
        MoreRoutes.RECOVERY_CODE to Permission.MANAGE_USERS,
        MoreRoutes.ACTIVITY_LOG to Permission.VIEW_ACTIVITY_LOG,
        MoreRoutes.GST_REPORT to Permission.GST_REPORT,
        MoreRoutes.LOYALTY to Permission.MANAGE_LOYALTY,
        MoreRoutes.DAY_END to Permission.CLOSE_DAY,
        MoreRoutes.DAY_CLOSING to Permission.CLOSE_DAY,
    )

    /** Every page that has been given an access rule, open or restricted. */
    val registered: Set<String> get() = OPEN + REQUIRED.keys

    /** The permission [route] needs, or null for a page open to everyone. A page with no rule at all is refused. */
    fun permissionFor(route: String): Permission? {
        if (route in OPEN) return null
        return REQUIRED[route] ?: Permission.MANAGE_USERS // unknown pages are Super Admin only
    }
}
