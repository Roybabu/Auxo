package com.auxo.pos.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.auxo.pos.R
import com.auxo.pos.core.auth.Permission

/** The five destinations shown in the main navigation bar, in display order. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    /** The tab's main page, which tapping the tab again goes back to. */
    val rootRoute: String = route,
    /** What a person needs to be shown this tab; null means everyone signed in. */
    val permission: Permission? = null,
) {
    HOME(route = "home", labelRes = R.string.nav_home, iconRes = R.drawable.ic_nav_home),
    BILLING(route = "billing", labelRes = R.string.nav_billing, iconRes = R.drawable.ic_nav_billing, rootRoute = BillingRoutes.POS, permission = Permission.SELL),
    INVENTORY(route = "inventory", labelRes = R.string.nav_inventory, iconRes = R.drawable.ic_nav_inventory, rootRoute = InventoryRoutes.LIST, permission = Permission.MANAGE_PRODUCTS),
    CUSTOMERS(route = "customers", labelRes = R.string.nav_customers, iconRes = R.drawable.ic_nav_customers, rootRoute = CustomerRoutes.LIST, permission = Permission.MANAGE_CUSTOMERS),
    MORE(route = "more", labelRes = R.string.nav_more, iconRes = R.drawable.ic_apps_outline, rootRoute = MoreRoutes.HOME),
    ;

    companion object {
        val START = HOME
    }
}
