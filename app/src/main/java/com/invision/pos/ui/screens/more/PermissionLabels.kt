package com.invision.pos.ui.screens.more

import androidx.annotation.StringRes
import com.invision.pos.R
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.PermissionGroup

@StringRes
fun PermissionGroup.titleRes(): Int = when (this) {
    PermissionGroup.SALES -> R.string.perm_group_sales
    PermissionGroup.FIGURES -> R.string.perm_group_figures
    PermissionGroup.INVENTORY -> R.string.perm_group_inventory
    PermissionGroup.CUSTOMERS -> R.string.perm_group_customers
    PermissionGroup.PURCHASING -> R.string.perm_group_purchasing
    PermissionGroup.SHOP -> R.string.perm_group_shop
}

/** The name shown beside an option's switch. Only options the Super Admin can switch on have one. */
@StringRes
fun Permission.labelRes(): Int = when (this) {
    Permission.PROCESS_RETURN -> R.string.perm_return
    Permission.CLOSE_DAY -> R.string.perm_close_day
    Permission.VIEW_DASHBOARD -> R.string.perm_dashboard
    Permission.VIEW_FINANCIALS -> R.string.perm_financials
    Permission.VIEW_REPORTS -> R.string.perm_reports
    Permission.MANAGE_PRODUCTS -> R.string.perm_products
    Permission.VIEW_STOCK_RECORDS -> R.string.perm_stock_records
    Permission.ADJUST_STOCK -> R.string.perm_adjust_stock
    Permission.MANAGE_CUSTOMERS -> R.string.perm_customers
    Permission.RECEIVE_ACCOUNT_PAYMENT -> R.string.perm_account_payment
    Permission.MANAGE_SUPPLIERS -> R.string.perm_suppliers
    Permission.MANAGE_PURCHASES -> R.string.perm_purchases
    Permission.RECEIVE_GOODS -> R.string.perm_receive_goods
    Permission.MANAGE_MENU -> R.string.perm_menu
    Permission.MANAGE_SETTINGS -> R.string.perm_settings
    Permission.VIEW_ACTIVITY_LOG -> R.string.perm_activity
    Permission.SELL, Permission.VIEW_INVOICES, Permission.BACKUP_AND_RESTORE, Permission.MANAGE_USERS -> R.string.perm_always
}

@StringRes
fun Permission.hintRes(): Int = when (this) {
    Permission.PROCESS_RETURN -> R.string.perm_return_hint
    Permission.CLOSE_DAY -> R.string.perm_close_day_hint
    Permission.VIEW_DASHBOARD -> R.string.perm_dashboard_hint
    Permission.VIEW_FINANCIALS -> R.string.perm_financials_hint
    Permission.VIEW_REPORTS -> R.string.perm_reports_hint
    Permission.MANAGE_PRODUCTS -> R.string.perm_products_hint
    Permission.VIEW_STOCK_RECORDS -> R.string.perm_stock_records_hint
    Permission.ADJUST_STOCK -> R.string.perm_adjust_stock_hint
    Permission.MANAGE_CUSTOMERS -> R.string.perm_customers_hint
    Permission.RECEIVE_ACCOUNT_PAYMENT -> R.string.perm_account_payment_hint
    Permission.MANAGE_SUPPLIERS -> R.string.perm_suppliers_hint
    Permission.MANAGE_PURCHASES -> R.string.perm_purchases_hint
    Permission.RECEIVE_GOODS -> R.string.perm_receive_goods_hint
    Permission.MANAGE_MENU -> R.string.perm_menu_hint
    Permission.MANAGE_SETTINGS -> R.string.perm_settings_hint
    Permission.VIEW_ACTIVITY_LOG -> R.string.perm_activity_hint
    Permission.SELL, Permission.VIEW_INVOICES, Permission.BACKUP_AND_RESTORE, Permission.MANAGE_USERS -> R.string.perm_always
}
