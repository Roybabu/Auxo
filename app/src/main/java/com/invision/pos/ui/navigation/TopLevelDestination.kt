package com.invision.pos.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.invision.pos.R

/** The five destinations shown in the main navigation bar, in display order. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    /** The tab's main page, which tapping the tab again goes back to. */
    val rootRoute: String = route,
) {
    HOME(route = "home", labelRes = R.string.nav_home, iconRes = R.drawable.ic_nav_home),
    BILLING(route = "billing", labelRes = R.string.nav_billing, iconRes = R.drawable.ic_nav_billing, rootRoute = BillingRoutes.POS),
    INVENTORY(route = "inventory", labelRes = R.string.nav_inventory, iconRes = R.drawable.ic_nav_inventory, rootRoute = InventoryRoutes.LIST),
    CUSTOMERS(route = "customers", labelRes = R.string.nav_customers, iconRes = R.drawable.ic_nav_customers, rootRoute = CustomerRoutes.LIST),
    MORE(route = "more", labelRes = R.string.nav_more, iconRes = R.drawable.ic_nav_more, rootRoute = MoreRoutes.HOME),
    ;

    companion object {
        val START = HOME
    }
}
