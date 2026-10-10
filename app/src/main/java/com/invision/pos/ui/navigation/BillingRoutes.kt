package com.invision.pos.ui.navigation

import com.invision.pos.viewmodel.InvoiceDetailViewModel

/** Routes inside the Billing tab. The tab's graph route is [TopLevelDestination.BILLING]. */
object BillingRoutes {
    private const val PREFIX = "billing"

    const val ARG_INVOICE_ID = InvoiceDetailViewModel.ARG_INVOICE_ID

    const val POS = "$PREFIX/pos"
    const val INVOICE = "$PREFIX/invoice/{$ARG_INVOICE_ID}"
    const val HISTORY = "$PREFIX/history"
    const val RETURN = "$PREFIX/return/{$ARG_INVOICE_ID}"
    const val SETTINGS = "$PREFIX/settings"

    fun invoice(invoiceId: Long): String = "$PREFIX/invoice/$invoiceId"

    fun returnFor(invoiceId: Long): String = "$PREFIX/return/$invoiceId"
}
