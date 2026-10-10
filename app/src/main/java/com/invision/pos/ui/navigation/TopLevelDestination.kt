package com.invision.pos.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.invision.pos.R

/** The five destinations shown in the main navigation bar, in display order. */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    HOME(route = "home", labelRes = R.string.nav_home, iconRes = R.drawable.ic_nav_home),
    BILLING(route = "billing", labelRes = R.string.nav_billing, iconRes = R.drawable.ic_nav_billing),
    INVENTORY(route = "inventory", labelRes = R.string.nav_inventory, iconRes = R.drawable.ic_nav_inventory),
    CUSTOMERS(route = "customers", labelRes = R.string.nav_customers, iconRes = R.drawable.ic_nav_customers),
    MORE(route = "more", labelRes = R.string.nav_more, iconRes = R.drawable.ic_nav_more),
    ;

    companion object {
        val START = HOME
    }
}
