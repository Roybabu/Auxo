package com.auxo.pos.ui.navigation

import com.auxo.pos.viewmodel.InvoiceDetailViewModel
import com.auxo.pos.viewmodel.InvoiceNotice

/** Routes inside the Billing tab. The tab's graph route is [TopLevelDestination.BILLING]. */
object BillingRoutes {
    private const val PREFIX = "billing"

    const val ARG_INVOICE_ID = InvoiceDetailViewModel.ARG_INVOICE_ID
    const val ARG_NOTICE = InvoiceDetailViewModel.ARG_NOTICE
    const val ARG_CHANGE = InvoiceDetailViewModel.ARG_CHANGE

    const val POS = "$PREFIX/pos"
    const val INVOICE = "$PREFIX/invoice/{$ARG_INVOICE_ID}?$ARG_NOTICE={$ARG_NOTICE}&$ARG_CHANGE={$ARG_CHANGE}"
    const val HISTORY = "$PREFIX/history"
    const val RETURN = "$PREFIX/return/{$ARG_INVOICE_ID}"

    fun invoice(invoiceId: Long): String = "$PREFIX/invoice/$invoiceId"

    /** Opens a just-saved invoice with a one-time message, e.g. the change to hand back. */
    fun savedInvoice(invoiceId: Long, notice: InvoiceNotice, changeMinor: Long = 0): String =
        "$PREFIX/invoice/$invoiceId?$ARG_NOTICE=${notice.name}&$ARG_CHANGE=$changeMinor"

    fun returnFor(invoiceId: Long): String = "$PREFIX/return/$invoiceId"
}
