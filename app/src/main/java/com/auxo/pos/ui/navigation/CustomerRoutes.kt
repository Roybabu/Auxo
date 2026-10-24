// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.navigation

import com.auxo.pos.viewmodel.CustomerDetailViewModel

/** Routes inside the Customers tab. The tab's graph route is [TopLevelDestination.CUSTOMERS]. */
object CustomerRoutes {
    private const val PREFIX = "customers"

    const val ARG_CUSTOMER_ID = CustomerDetailViewModel.ARG_CUSTOMER_ID

    const val LIST = "$PREFIX/list"
    const val DETAIL = "$PREFIX/detail/{$ARG_CUSTOMER_ID}"
    const val EDITOR = "$PREFIX/editor?$ARG_CUSTOMER_ID={$ARG_CUSTOMER_ID}"
    const val PAYMENT = "$PREFIX/payment/{$ARG_CUSTOMER_ID}"
    const val HISTORY = "$PREFIX/history/{$ARG_CUSTOMER_ID}"

    fun detail(customerId: Long): String = "$PREFIX/detail/$customerId"

    fun newCustomer(): String = "$PREFIX/editor"

    fun editCustomer(customerId: Long): String = "$PREFIX/editor?$ARG_CUSTOMER_ID=$customerId"

    fun payment(customerId: Long): String = "$PREFIX/payment/$customerId"

    fun history(customerId: Long): String = "$PREFIX/history/$customerId"
}
