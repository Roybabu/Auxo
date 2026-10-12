package com.invision.pos.ui.navigation

import com.invision.pos.viewmodel.AlertsTab
import com.invision.pos.viewmodel.PurchaseOrderDetailViewModel
import com.invision.pos.viewmodel.PurchaseTab
import com.invision.pos.viewmodel.ReceiptDetailViewModel
import com.invision.pos.viewmodel.StockAlertsViewModel
import com.invision.pos.viewmodel.PurchaseListViewModel
import com.invision.pos.viewmodel.SupplierEditorViewModel

/**
 * Suppliers, purchasing, stock alerts and stock history. These pages belong to the
 * Inventory tab's graph, so the Inventory tab stays selected while they are open.
 */
object StockRoutes {
    private const val PREFIX = "stock"

    const val ARG_SUPPLIER_ID = SupplierEditorViewModel.ARG_SUPPLIER_ID
    const val ARG_ORDER_ID = PurchaseOrderDetailViewModel.ARG_ORDER_ID
    const val ARG_RECEIPT_ID = ReceiptDetailViewModel.ARG_RECEIPT_ID
    const val ARG_PURCHASE_TAB = PurchaseListViewModel.ARG_TAB
    const val ARG_ALERTS_TAB = StockAlertsViewModel.ARG_TAB

    const val SUPPLIERS = "$PREFIX/suppliers"
    const val SUPPLIER_EDITOR = "$PREFIX/supplier?$ARG_SUPPLIER_ID={$ARG_SUPPLIER_ID}"
    const val PURCHASES = "$PREFIX/purchases?$ARG_PURCHASE_TAB={$ARG_PURCHASE_TAB}"
    const val ORDER = "$PREFIX/order/{$ARG_ORDER_ID}"
    const val ORDER_EDITOR = "$PREFIX/order-editor?$ARG_ORDER_ID={$ARG_ORDER_ID}"
    const val RECEIVE = "$PREFIX/receive?$ARG_ORDER_ID={$ARG_ORDER_ID}"
    const val RECEIPT = "$PREFIX/receipt/{$ARG_RECEIPT_ID}"
    const val MOVEMENTS = "$PREFIX/movements"
    const val ALERTS = "$PREFIX/alerts?$ARG_ALERTS_TAB={$ARG_ALERTS_TAB}"

    fun suppliers(): String = SUPPLIERS

    fun newSupplier(): String = "$PREFIX/supplier"

    fun editSupplier(supplierId: Long): String = "$PREFIX/supplier?$ARG_SUPPLIER_ID=$supplierId"

    fun purchases(tab: PurchaseTab = PurchaseTab.ORDERS): String = "$PREFIX/purchases?$ARG_PURCHASE_TAB=${tab.name}"

    fun order(orderId: Long): String = "$PREFIX/order/$orderId"

    fun newOrder(): String = "$PREFIX/order-editor"

    fun editOrder(orderId: Long): String = "$PREFIX/order-editor?$ARG_ORDER_ID=$orderId"

    /** Receives goods against [orderId], or without an order when it is null. */
    fun receive(orderId: Long? = null): String = if (orderId == null) "$PREFIX/receive" else "$PREFIX/receive?$ARG_ORDER_ID=$orderId"

    fun receipt(receiptId: Long): String = "$PREFIX/receipt/$receiptId"

    fun movements(): String = MOVEMENTS

    fun alerts(tab: AlertsTab = AlertsTab.LOW_STOCK): String = "$PREFIX/alerts?$ARG_ALERTS_TAB=${tab.name}"
}

/** Restaurant menu and reports, inside the More tab's graph. */
object MoreRoutes {
    private const val PREFIX = "more"

    const val ARG_MENU_ITEM_ID = com.invision.pos.viewmodel.MenuEditorViewModel.ARG_MENU_ITEM_ID

    const val HOME = "$PREFIX/home"
    const val MENU = "$PREFIX/menu"
    const val MENU_EDITOR = "$PREFIX/menu-item?$ARG_MENU_ITEM_ID={$ARG_MENU_ITEM_ID}"
    const val REPORTS = "$PREFIX/reports"
    const val BACKUP = "$PREFIX/backup"
    const val SHOP_SETTINGS = "$PREFIX/shop-settings"
    const val STORE_DETAILS = "$PREFIX/store-details"
    const val APPEARANCE = "$PREFIX/appearance"
    const val USER_MANAGEMENT = "$PREFIX/users"
    const val ACTIVITY_LOG = "$PREFIX/activity"
    const val RECOVERY_CODE = "$PREFIX/recovery-code"
    const val QUICK_UNLOCK = "$PREFIX/quick-unlock"
    const val LOYALTY = "$PREFIX/loyalty"
    const val ARG_CLOSING_ID = "closingId"
    const val DAY_END = "$PREFIX/day-end"
    const val GST_REPORT = "$PREFIX/gst-report"
    const val DAY_CLOSING = "$PREFIX/day-end/{$ARG_CLOSING_ID}"

    fun dayClosing(closingId: Long): String = "$PREFIX/day-end/$closingId"

    fun newMenuItem(): String = "$PREFIX/menu-item"

    fun editMenuItem(menuItemId: Long): String = "$PREFIX/menu-item?$ARG_MENU_ITEM_ID=$menuItemId"
}
