package com.invision.pos.ui.navigation

import com.invision.pos.viewmodel.CustomerDetailViewModel

/** Routes inside the Customers tab. The tab's graph route is [TopLevelDestination.CUSTOMERS]. */
object CustomerRoutes {
    private const val PREFIX = "customers"

    const val ARG_CUSTOMER_ID = CustomerDetailViewModel.ARG_CUSTOMER_ID

    const val LIST = "$PREFIX/list"
    const val DETAIL = "$PREFIX/detail/{$ARG_CUSTOMER_ID}"
    const val EDITOR = "$PREFIX/editor?$ARG_CUSTOMER_ID={$ARG_CUSTOMER_ID}"
    const val PAYMENT = "$PREFIX/payment/{$ARG_CUSTOMER_ID}"

    fun detail(customerId: Long): String = "$PREFIX/detail/$customerId"

    fun newCustomer(): String = "$PREFIX/editor"

    fun editCustomer(customerId: Long): String = "$PREFIX/editor?$ARG_CUSTOMER_ID=$customerId"

    fun payment(customerId: Long): String = "$PREFIX/payment/$customerId"
}
